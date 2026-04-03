package com.inmobiliaria.property_service.controller;

import com.inmobiliaria.property_service.domain.OperationType;
import com.inmobiliaria.property_service.dto.request.*;
import com.inmobiliaria.property_service.dto.response.PropertyResponse;
import com.inmobiliaria.property_service.service.PropertyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    // --- AGREGA ESTE MÉTODO ---
    @GetMapping("/agent/{agentId}")
    public List<PropertyResponse> findByAgent(@PathVariable String agentId) {
        // Usamos el método que ya tienes creado en tu PropertyService
        return propertyService.findByAgent(agentId);
    }

    @GetMapping
    public List<PropertyResponse> findAll(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) OperationType operationType,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String agentId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        // SEGURIDAD: Validar que auth no sea nulo antes de usarlo
        if (auth == null || !auth.isAuthenticated()) {
             return Collections.emptyList(); 
        }
        
        String currentUserId = (String) auth.getPrincipal();
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        // LLamada unificada: El servicio filtra por seguridad automáticamente
    return propertyService.findWithFilters(title, type, status, operationType, minPrice, maxPrice, agentId, currentUserId, roles);
    }

    @PostMapping
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public PropertyResponse create(
            @Valid @RequestBody PropertyRequest request,
            @RequestHeader("X-Auth-User-Id") String agentId) {
        return propertyService.create(request, agentId);
    }

    @GetMapping("/{id}")
    public PropertyResponse findById(@PathVariable String id) {
        return propertyService.findById(id);
    }

    @PatchMapping("/{id}/price")
    @PreAuthorize("hasRole('ADMIN')")
    public PropertyResponse updatePrice(
            @PathVariable String id,
            @Valid @RequestBody UpdatePriceRequest request,
            @RequestHeader("X-Auth-User-Id") String adminId) {
        return propertyService.updatePrice(id, request.newPrice(), adminId);
    }

    @PatchMapping("/{id}/assign-agent")
    @PreAuthorize("hasRole('ADMIN')")
    public PropertyResponse assignAgent(
            @PathVariable String id,
            @Valid @RequestBody AssignAgentRequest request,
            @RequestHeader("X-Auth-User-Id") String adminId) {
        return propertyService.assignAgent(id, request, adminId);
    }

    @PostMapping("/{id}/images/upload")
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public Map<String, String> getUploadUrl(@PathVariable String id) {
        return propertyService.generatePresignedUrl(id);
    }

    @PatchMapping("/{id}/operation-type")
    @PreAuthorize("hasRole('ADMIN')")
    public PropertyResponse updateOperationType(
            @PathVariable String id,
            @Valid @RequestBody UpdateOperationTypeRequest request) {
        return propertyService.updateOperationType(id, request.operationType());
    }
}