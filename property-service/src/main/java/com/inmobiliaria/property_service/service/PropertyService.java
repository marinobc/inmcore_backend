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
    private final MongoTemplate mongoTemplate; // Inyectado para búsquedas dinámicas

    public Map<String, Object> findWithFilters(
            String title, String type, String status,
            OperationType operationType,
            Double minPrice, Double maxPrice, String agentId,
            String currentUserId, List<String> roles,
            String sortBy, String sortOrder, int page, int pageSize) {

        Query query = new Query();
        List<Criteria> filters = new ArrayList<>();

        // 1. Filtros de la Barra de Herramientas
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

        // 2. Seguridad por Rol (Lógica de visibilidad)
        // Si NO es ADMIN, aplicamos restricción de visibilidad
        if (!roles.contains("ROLE_ADMIN")) {
            Criteria securityCriteria = new Criteria().orOperator(
                    Criteria.where("assignedAgentId").is(currentUserId),
                    Criteria.where("accessPolicy").in(currentUserId, "ROLE_AGENT"));
            filters.add(securityCriteria);
        }

        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters.toArray(new Criteria[0])));
        }

        // Ordenación
        Sort.Direction direction = "DESC".equalsIgnoreCase(sortOrder)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        String sortField = switch (sortBy) {
            case "title" -> "title";
            case "m2" -> "m2";
            case "rooms" -> "rooms";
            case "status" -> "status";
            default -> "price";
        };

        // Contar total sin paginar
        long total = mongoTemplate.count(query, PropertyDocument.class);

        // Aplicar ordenación y paginación
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

    // Métodos existentes simplificados para usar la misma lógica
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

    public Map<String, String> generatePresignedUrl(String id) {
        String fileName = UUID.randomUUID() + ".jpg";
        String objectKey = "properties/" + id + "/images/" + fileName;
        return Map.of(
                "uploadUrl", "http://localhost:9000/bucket/" + objectKey + "?sig=mock",
                "publicUrl", "http://localhost:9000/bucket/" + objectKey);
    }

    public PropertyResponse addImages(String id, List<String> urls) {
        PropertyDocument prop = propertyRepository.findById(id).orElseThrow();
        prop.getImageUrls().addAll(urls);
        return mapToResponse(propertyRepository.save(prop));
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

    public PropertyResponse updateAccessPolicy(String id, Set<String> accessPolicy, String userId) {
        PropertyDocument prop = propertyRepository.findById(id).orElseThrow();
        prop.setAccessPolicy(accessPolicy);
        return mapToResponse(propertyRepository.save(prop));
    }

    public List<PropertyResponse> findByOwner(String ownerId) {
        return propertyRepository.findByOwnerId(ownerId).stream().map(this::mapToResponse).toList();
    }

    public PropertyResponse assignOwner(String id, String ownerId, String adminId) {
        PropertyDocument prop = propertyRepository.findById(id).orElseThrow();
        prop.setOwnerId(ownerId);
        return mapToResponse(propertyRepository.save(prop));
    }

    public PropertyResponse updateOperationType(String id, OperationType newType) {
        PropertyDocument prop = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inmueble no encontrado"));

        prop.setOperationType(newType);
        prop.setUpdatedAt(Instant.now());

        return mapToResponse(propertyRepository.save(prop));
    }

    private PropertyResponse mapToResponse(PropertyDocument doc) {
        return new PropertyResponse(
                doc.getId(), doc.getTitle(), doc.getAddress(), doc.getPrice(),
                doc.getType(), doc.getOperationType(), doc.getM2(), doc.getRooms(), doc.getStatus(),
                doc.getAssignedAgentId(), doc.getImageUrls(),
                doc.getAssignmentHistory(), doc.getPriceHistory(),
                doc.getAccessPolicy());
    }
}