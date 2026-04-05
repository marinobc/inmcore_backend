package com.inmobiliaria.property_service.service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.inmobiliaria.property_service.client.IdentityClient;
import com.inmobiliaria.property_service.domain.*;
import com.inmobiliaria.property_service.dto.request.*;
import com.inmobiliaria.property_service.dto.response.PropertyResponse;
import com.inmobiliaria.property_service.exception.AccessDeniedException;
import com.inmobiliaria.property_service.exception.ResourceNotFoundException;
import com.inmobiliaria.property_service.exception.ValidationException;
import com.inmobiliaria.property_service.repository.PropertyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final IdentityClient identityClient;
    private final MongoTemplate mongoTemplate;
    private final ImageService imageService;

    /**
     * Búsqueda avanzada con filtros dinámicos y seguridad por rol.
     */
    public Map<String, Object> findWithFilters(
            String title, String type, String status,
            OperationType operationType,
            Double minPrice, Double maxPrice, String agentId,
            String currentUserId, List<String> roles,
            String sortBy, String sortOrder, int page, int pageSize) {

        Query query = new Query();
        List<Criteria> filters = new ArrayList<>();

        filters.add(Criteria.where("deleted").is(false));

        if (title != null && !title.isBlank()) {
            filters.add(Criteria.where("title").regex(title, "i"));
        }
        if (type != null && !type.isBlank()) {
            filters.add(Criteria.where("type").is(type));
        }
        if (status != null && !status.isBlank()) {
            filters.add(Criteria.where("status").is(status));
        }
        if (operationType != null) {
            filters.add(Criteria.where("operationType").is(operationType));
        }
        if (minPrice != null) {
            filters.add(Criteria.where("price").gte(minPrice));
        }
        if (maxPrice != null) {
            filters.add(Criteria.where("price").lte(maxPrice));
        }
        if (agentId != null && !agentId.isBlank()) {
            filters.add(Criteria.where("assignedAgentId").is(agentId));
        }

        // Seguridad: Si no es ADMIN, solo ve sus asignadas o las permitidas por política
        if (!roles.contains("ROLE_ADMIN")) {
            Criteria securityCriteria = new Criteria().orOperator(
                    Criteria.where("assignedAgentId").is(currentUserId),
                    Criteria.where("accessPolicy").in(currentUserId, "ROLE_AGENT"));
            filters.add(securityCriteria);
        }

        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters.toArray(new Criteria[0])));
        }

        Sort.Direction direction = "DESC".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortField = switch (sortBy) {
            case "title" -> "title";
            case "m2" -> "m2";
            case "rooms" -> "rooms";
            case "status" -> "status";
            default -> "price";
        };

        long total = mongoTemplate.count(query, PropertyDocument.class);

        query.with(Sort.by(direction, sortField))
                .skip((long) page * pageSize)
                .limit(pageSize);

        List<PropertyResponse> data = mongoTemplate.find(query, PropertyDocument.class).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", data);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("totalElements", total);
        result.put("totalPages", (int) Math.ceil((double) total / pageSize));
        return result;
    }

    public List<PropertyResponse> findAll() {
        return propertyRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    public PropertyResponse findById(String id) {
        return propertyRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado: " + id));
    }

    public PropertyResponse create(PropertyRequest request, String agentId) {
        PropertyDocument property = PropertyDocument.builder()
                .title(request.title())
                .address(request.address())
                .price(request.price())
                .type(request.type())
                .operationType(request.operationType())
                .m2(request.m2())
                .rooms(request.rooms())
                .status("DISPONIBLE")
                .assignedAgentId(agentId)
                .ownerId(request.ownerId())
                .accessPolicy(request.accessPolicy() != null ? request.accessPolicy() : new HashSet<>())
                .build();
        property.setCreatedAt(Instant.now());
        property.setCreatedBy(agentId);
        return mapToResponse(propertyRepository.save(property));
    }

    public PropertyResponse updatePrice(String id, Double newPrice, String adminId) {
        PropertyDocument prop = propertyRepository.findById(id).orElseThrow();
        prop.getPriceHistory().add(new PriceHistory(prop.getPrice(), newPrice, Instant.now(), adminId));
        prop.setPrice(newPrice);
        return mapToResponse(propertyRepository.save(prop));
    }

    public PropertyResponse assignAgent(String id, AssignAgentRequest request, String adminId) {
        PropertyDocument prop = propertyRepository.findById(id).orElseThrow();
        var agent = identityClient.findById(request.agentId());
        if (!"ACTIVE".equals(agent.status()))
            throw new RuntimeException("Agente inactivo");
        prop.getAssignmentHistory().add(new AssignmentHistory(prop.getAssignedAgentId(), Instant.now(), adminId));
        prop.setAssignedAgentId(request.agentId());
        return mapToResponse(propertyRepository.save(prop));
    }

    public List<PropertyResponse> findByAgent(String agentId) {
        return propertyRepository.findByAssignedAgentId(agentId).stream().map(this::mapToResponse).toList();
    }

    public List<PropertyResponse> findByOwner(String ownerId) {
        return propertyRepository.findByOwnerId(ownerId).stream().map(this::mapToResponse).toList();
    }

    public PropertyResponse assignOwner(String id, String ownerId, String adminId) {
        PropertyDocument prop = propertyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + id));
        
        // Optional: Validate owner exists in identity service
        try {
            var owner = identityClient.findById(ownerId);
            if (!"ACTIVE".equals(owner.status())) {
                throw new ValidationException("Owner is not active");
            }
        } catch (Exception e) {
            log.warn("Could not validate owner: {}", e.getMessage());
        }
        
        prop.setOwnerId(ownerId);
        prop.setUpdatedAt(Instant.now());
        
        // Add to audit/assignment history if needed
        if (prop.getAssignmentHistory() == null) {
            prop.setAssignmentHistory(new ArrayList<>());
        }
        prop.getAssignmentHistory().add(new AssignmentHistory(
            prop.getAssignedAgentId(), 
            Instant.now(), 
            adminId
        ));
        
        return mapToResponse(propertyRepository.save(prop));
    }

    public PropertyResponse updateOperationType(String id, OperationType newType) {
        PropertyDocument prop = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado"));
        prop.setOperationType(newType);
        prop.setUpdatedAt(Instant.now());
        return mapToResponse(propertyRepository.save(prop));
    }

    /**
     * Mapea el documento a DTO incluyendo la generación de URLs temporales firmadas para imágenes.
     */
    public PropertyResponse mapToResponse(PropertyDocument doc) {
        List<String> urls = new ArrayList<>();
        
        if (doc.getImages() != null && !doc.getImages().isEmpty()) {
            urls = doc.getImages().stream()
                    .map(img -> imageService.generateTemporaryImageUrl(img))
                    .collect(Collectors.toList());
        } else if (doc.getImageUrls() != null) {
            urls = doc.getImageUrls();
        }

        return new PropertyResponse(
                doc.getId(), 
                doc.getTitle(), 
                doc.getAddress(), 
                doc.getPrice(),
                doc.getType(), 
                doc.getOperationType(), 
                doc.getM2(), 
                doc.getRooms(), 
                doc.getStatus(),
                doc.getAssignedAgentId(), 
                doc.getOwnerId(), // <--- PASAR EL OWNER ID AQUÍ
                urls, 
                doc.getAssignmentHistory() != null ? doc.getAssignmentHistory() : new ArrayList<>(),
                doc.getPriceHistory() != null ? doc.getPriceHistory() : new ArrayList<>(),
                doc.getAccessPolicy() != null ? doc.getAccessPolicy() : new HashSet<>()
        );
    }

    /**
     * CORRECCIÓN CRÍTICA: Eliminación de imagen estrictamente por ID.
     * Esto evita que el API Gateway detecte caracteres maliciosos (//) en la URL de la petición.
     */
    public PropertyResponse deleteImage(String propertyId, String imageId) {
        PropertyDocument property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));
        
        if (property.getImages() != null) {
            // Buscamos estrictamente por el ID interno generado en MongoDB (UUID)
            Optional<ImageMetadata> imgOpt = property.getImages().stream()
                    .filter(img -> img.getId().equals(imageId))
                    .findFirst();

            if (imgOpt.isPresent()) {
                log.info("Eliminando imagen con ID: {} de la propiedad: {}", imageId, propertyId);
                imageService.deleteImage(propertyId, imageId);
            } else {
                log.error("No se pudo borrar: la imagen con ID {} no pertenece a la propiedad {}", imageId, propertyId);
                throw new ResourceNotFoundException("La imagen solicitada no existe en este inmueble");
            }
        }
        
        return mapToResponse(propertyRepository.findById(propertyId).orElseThrow());
    }

    public void deleteProperty(String id, String adminId) {
        PropertyDocument property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado: " + id));
        
        property.setDeleted(true);
        property.setStatus("ELIMINADO"); // Opcional: cambiar el status visual también
        property.setUpdatedAt(Instant.now());
        
        propertyRepository.save(property);
        
        log.info("Propiedad {} marcada como eliminada (lógico) por admin: {}", id, adminId);
    }

    public PropertyResponse updateStatus(String id, String newStatus, String currentUserId, List<String> roles) {
        PropertyDocument prop = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado"));

        // --- Backend Task 1: Validation (PA1) ---
        boolean isAdmin = roles.contains("ROLE_ADMIN");
        boolean isAssignedAgent = currentUserId.equals(prop.getAssignedAgentId());

        if (!isAdmin && !isAssignedAgent) {
            throw new AccessDeniedException("No tiene autorización para cambiar el estado de este inmueble. Solo el responsable o un administrador pueden hacerlo.");
        }

        // --- Backend Task 2: Register History ---
        if (prop.getStatusHistory() == null) {
            prop.setStatusHistory(new ArrayList<>());
        }

        prop.getStatusHistory().add(StatusHistory.builder()
                .oldStatus(prop.getStatus())
                .newStatus(newStatus.toUpperCase())
                .changedAt(Instant.now())
                .changedBy(currentUserId)
                .build());

        prop.setStatus(newStatus.toUpperCase());
        prop.setUpdatedAt(Instant.now());

        return mapToResponse(propertyRepository.save(prop));
    }
}