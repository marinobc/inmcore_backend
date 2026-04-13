package com.inmobiliaria.property_service.controller;

import com.inmobiliaria.property_service.domain.OperationType;
import com.inmobiliaria.property_service.domain.StatusHistory;
import com.inmobiliaria.property_service.dto.request.*;
import com.inmobiliaria.property_service.dto.response.PropertyResponse;
import com.inmobiliaria.property_service.dto.response.ResponsableResponse;
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

    // --- READ OPERATIONS ---

    @GetMapping("/agent/{agentId}")
    public List<PropertyResponse> findByAgent(@PathVariable String agentId) {
        return propertyService.findByAgent(agentId);
    }

    @PatchMapping("/{id}/agent-update")
    @PreAuthorize("hasRole('AGENT')")
    public PropertyResponse updatePropertyAsAgent(
            @PathVariable String id,
            @Valid @RequestBody AgentPropertyUpdateRequest request,
            @RequestHeader("X-Auth-User-Id") String agentId) {
        return propertyService.updatePropertyAsAgent(id, request, agentId);
    }

    @GetMapping
    public Map<String, Object> findAll(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) OperationType operationType,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false, defaultValue = "price") String sortBy,
            @RequestParam(required = false, defaultValue = "ASC") String sortOrder,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "9") int pageSize) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Collections.emptyMap();
        }

        String currentUserId = (String) auth.getPrincipal();
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return propertyService.findWithFilters(title, type, status, operationType, minPrice, maxPrice, agentId,
                currentUserId, roles, sortBy, sortOrder, page, pageSize);
    }

    @GetMapping("/{id}")
    public PropertyResponse findById(@PathVariable String id) {
        return propertyService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PropertyResponse updateProperty(
            @PathVariable String id,
            @Valid @RequestBody PropertyRequest request,
            @RequestHeader("X-Auth-User-Id") String adminId) {
        return propertyService.updateProperty(id, request, adminId);
    }

    // --- WRITE OPERATIONS (PROPERTY AGGREGATE) ---

    @PostMapping
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public PropertyResponse create(
            @Valid @RequestBody PropertyRequest request,
            @RequestHeader("X-Auth-User-Id") String agentId) {
        return propertyService.create(request, agentId);
    }

    @PatchMapping("/{id}/assign-owner")
    @PreAuthorize("hasRole('ADMIN')")
    public PropertyResponse assignOwner(
            @PathVariable String id,
            @RequestBody AssignOwnerRequest request,
            @RequestHeader("X-Auth-User-Id") String adminId) {
        return propertyService.assignOwner(id, request.ownerId(), adminId);
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

    @PatchMapping("/{id}/operation-type")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AGENT')")
    public PropertyResponse updateOperationType(
            @PathVariable String id,
            @Valid @RequestBody UpdateOperationTypeRequest request) {
        return propertyService.updateOperationType(id, request.operationType());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable String id, @RequestHeader("X-Auth-User-Id") String adminId) {
        propertyService.deleteProperty(id, adminId);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public PropertyResponse updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateStatusRequest request,
            @RequestHeader("X-Auth-User-Id") String userId) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return propertyService.updateStatus(id, request.status(), userId, roles);
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AGENT')")
    public List<StatusHistory> getStatusHistory(@PathVariable String id) {
        return propertyService.findById(id).statusHistory();
    }

    @GetMapping("/{id}/responsable")
    public ResponsableResponse getResponsable(@PathVariable String id) {
        return propertyService.getResponsable(id);
    }

    @GetMapping("/owner/{ownerId}")
    public List<PropertyResponse> findByOwner(@PathVariable String ownerId) {
        return propertyService.findByOwner(ownerId);
    }
}