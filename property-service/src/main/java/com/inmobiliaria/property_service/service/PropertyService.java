package com.inmobiliaria.property_service.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.inmobiliaria.property_service.client.IdentityClient;
import com.inmobiliaria.property_service.domain.AssignmentHistory;
import com.inmobiliaria.property_service.domain.PriceHistory;
import com.inmobiliaria.property_service.domain.PropertyDocument;
import com.inmobiliaria.property_service.dto.request.AssignAgentRequest;
import com.inmobiliaria.property_service.dto.request.PropertyRequest;
import com.inmobiliaria.property_service.dto.response.PropertyResponse;
import com.inmobiliaria.property_service.exception.ResourceNotFoundException;
import com.inmobiliaria.property_service.repository.PropertyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final IdentityClient identityClient;

    public List<PropertyResponse> findAll() {
        return propertyRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public PropertyResponse findById(String id) {
        return propertyRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado: " + id));
    }

    // HU 1: Registro de Inmueble con estado inicial "DISPONIBLE"
    public PropertyResponse create(PropertyRequest request, String agentId) {
        PropertyDocument property = PropertyDocument.builder()
                .title(request.title())
                .address(request.address())
                .price(request.price())
                .type(request.type())
                .m2(request.m2())
                .rooms(request.rooms())
                .status("DISPONIBLE") // Requerimiento HU1
                .assignedAgentId(agentId)
                .ownerId(request.ownerId())
                .imageUrls(new ArrayList<>())
                .assignmentHistory(new ArrayList<>())
                .priceHistory(new ArrayList<>())
                .accessPolicy(request.accessPolicy() != null ? request.accessPolicy() : new HashSet<>())
                .build();

        property.setCreatedAt(Instant.now());
        property.setCreatedBy(agentId);

        return mapToResponse(propertyRepository.save(property));
    }

    // HU 1: Generar URL prefirmada (Mock de lógica S3/Cloud)
    public Map<String, String> generatePresignedUrl(String id) {
        // Verificación adicional de permisos para seguridad en servicio
        if (!hasAccessToProperty(id)) {
            throw new RuntimeException("Acceso denegado para generar URL prefirmada");
        }

        // Validar que la propiedad existe
        propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado"));

        String fileName = UUID.randomUUID().toString() + ".jpg";
        // Estructura requerida: properties/{propertyId}/images/
        String objectKey = "properties/" + id + "/images/" + fileName;

        // Aquí se integraría el SDK de AWS S3 o MinIO
        String uploadUrl = "http://localhost:9000/bucket/" + objectKey + "?signature=mock_sig";
        String publicUrl = "http://localhost:9000/bucket/" + objectKey;

        return Map.of(
            "uploadUrl", uploadUrl,
            "publicUrl", publicUrl
        );
    }

    public List<PropertyResponse> searchByTerm(String term) {
        // Buscamos coincidencias parciales (case-insensitive) en título o dirección
        return propertyRepository.findAll().stream()
                .filter(p -> p.getTitle().toLowerCase().contains(term.toLowerCase()) || 
                            p.getAddress().toLowerCase().contains(term.toLowerCase()))
                .limit(10) // Limitamos para no sobrecargar el dropdown
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private boolean hasAccessToProperty(String propertyId) {
        var property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado"));

        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        String userId = String.valueOf(auth.getPrincipal());
        Set<String> roles = auth.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .collect(Collectors.toSet());

        // Lógica de permisos por rol
        boolean isAdmin = roles.contains("ROLE_ADMIN");
        boolean isAgent = roles.contains("ROLE_AGENT");
        boolean isAssignedAgent = userId.equalsIgnoreCase(property.getAssignedAgentId());

        // Admin siempre tiene acceso
        if (isAdmin) {
            return true;
        }

        // Agente asignado tiene acceso
        if (isAssignedAgent) {
            return true;
        }

        // Agente general tiene acceso si está en la política de acceso
        boolean isGeneralAgentAllowed = isAgent && property.getAccessPolicy().contains("ROLE_AGENT");

        if (isGeneralAgentAllowed) {
            return true;
        }

        // Verificar políticas específicas de usuario o rol
        boolean isAllowedByPolicy = property.getAccessPolicy().stream().anyMatch(policy -> {
            String normalized = policy.trim();
            if (normalized.startsWith("ROLE_")) {
                return roles.contains(normalized.toUpperCase());
            }
            return normalized.equalsIgnoreCase(userId);
        });

        return isAllowedByPolicy;
    }

    // HU 1: Confirmar carga de imágenes
    public PropertyResponse addImages(String id, List<String> urls) {
        PropertyDocument prop = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado"));
        
        prop.getImageUrls().addAll(urls);
        prop.setUpdatedAt(Instant.now());
        return mapToResponse(propertyRepository.save(prop));
    }

    // HU 2: Modificar precios con historial
    public PropertyResponse updatePrice(String id, Double newPrice, String adminId) {
        PropertyDocument prop = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado"));

        // Registrar en historial: precio anterior, fecha y usuario (HU2)
        PriceHistory history = PriceHistory.builder()
                .oldPrice(prop.getPrice())
                .newPrice(newPrice)
                .changedAt(Instant.now())
                .changedBy(adminId)
                .build();

        prop.getPriceHistory().add(history);
        prop.setPrice(newPrice);
        prop.setUpdatedAt(Instant.now());

        return mapToResponse(propertyRepository.save(prop));
    }

    // HU 3: Asignar agente con validación de estado activo
    public PropertyResponse assignAgent(String id, AssignAgentRequest request, String adminId) {
        PropertyDocument prop = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado"));

        // Comunicación con user-service vía Feign para validar agente (HU3)
        var agent = identityClient.findById(request.agentId());
        if (!"ACTIVE".equals(agent.status())) {
            throw new RuntimeException("El agente no está disponible o ha sido dado de baja");
        }

        // Registrar historial de reasignación (HU3)
        if (prop.getAssignedAgentId() != null) {
            prop.getAssignmentHistory().add(new AssignmentHistory(
                prop.getAssignedAgentId(),
                Instant.now(),
                adminId
            ));
        }

        prop.setAssignedAgentId(request.agentId());
        prop.setUpdatedAt(Instant.now());
        return mapToResponse(propertyRepository.save(prop));
    }

    public List<PropertyResponse> findByAgent(String agentId) {
        return propertyRepository.findByAssignedAgentId(agentId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // HU: Definir política de acceso por roles/usuarios a la propiedad
    public PropertyResponse updateAccessPolicy(String id, Set<String> accessPolicy, String userId) {
        PropertyDocument prop = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado"));

        prop.setAccessPolicy(accessPolicy);
        prop.setUpdatedAt(Instant.now());

        return mapToResponse(propertyRepository.save(prop));
    }

    public List<PropertyResponse> findByOwner(String ownerId) {
        return propertyRepository.findByOwnerId(ownerId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public PropertyResponse assignOwner(String id, String ownerId, String adminId) {
        PropertyDocument prop = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado: " + id));
        
        validateOwnerExists(ownerId);
        
        prop.setOwnerId(ownerId);
        prop.setUpdatedAt(Instant.now());
        
        return mapToResponse(propertyRepository.save(prop));
    }

    private PropertyResponse mapToResponse(PropertyDocument doc) {
        return new PropertyResponse(
                doc.getId(), doc.getTitle(), doc.getAddress(), doc.getPrice(),
                doc.getType(), doc.getM2(), doc.getRooms(), doc.getStatus(),
                doc.getAssignedAgentId(), doc.getImageUrls(),
                doc.getAssignmentHistory(), doc.getPriceHistory(),
                doc.getAccessPolicy()
        );
    }
    private void validateOwnerExists(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return;
        }
        
        try {
            var owner = identityClient.findById(ownerId);
            if (owner == null) {
                throw new IllegalArgumentException("El propietario especificado no existe");
            }
        } catch (Exception e) {
            log.warn("Could not validate owner existence: {}", e.getMessage());
        }
    }
}