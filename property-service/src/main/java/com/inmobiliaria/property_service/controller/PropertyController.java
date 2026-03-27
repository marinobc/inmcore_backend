package com.inmobiliaria.property_service.controller;

import com.inmobiliaria.property_service.dto.request.AccessPolicyRequest;
import com.inmobiliaria.property_service.dto.request.AssignAgentRequest;
import com.inmobiliaria.property_service.dto.request.PropertyRequest;
import com.inmobiliaria.property_service.dto.request.UpdatePriceRequest;
import com.inmobiliaria.property_service.dto.response.PropertyResponse;
import com.inmobiliaria.property_service.service.PropertyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @PostMapping
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public PropertyResponse create(
            @Valid @RequestBody PropertyRequest request,
            @RequestHeader("X-Auth-User-Id") String agentId) {
        return propertyService.create(request, agentId);
    }

    @PostMapping("/{id}/images/upload")
    @PreAuthorize("hasRole('AGENT')")
    public Map<String, String> getUploadUrl(@PathVariable String id) {
        return propertyService.generatePresignedUrl(id);
    }

    @PatchMapping("/{id}/assign-agent")
    @PreAuthorize("hasRole('ADMIN')")
    public PropertyResponse assignAgent(
            @PathVariable String id,
            @Valid @RequestBody AssignAgentRequest request,
            @RequestHeader("X-Auth-User-Id") String adminId) {
        return propertyService.assignAgent(id, request, adminId);
    }

    @GetMapping("/agent/{agentId}")
    public List<PropertyResponse> getByAgent(@PathVariable String agentId) {
        return propertyService.findByAgent(agentId);
    }
    @PatchMapping("/{id}/price")
    
    @PreAuthorize("hasRole('ADMIN')")
    public PropertyResponse updatePrice(
            @PathVariable String id,
            @Valid @RequestBody UpdatePriceRequest request,
            @RequestHeader("X-Auth-User-Id") String adminId) {
        return propertyService.updatePrice(id, request.newPrice(), adminId);
    }

    @PatchMapping("/{id}/access-policy")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AGENT')")
    public PropertyResponse updateAccessPolicy(
            @PathVariable String id,
            @Valid @RequestBody AccessPolicyRequest request,
            @RequestHeader("X-Auth-User-Id") String userId) {
        return propertyService.updateAccessPolicy(id, request.accessPolicy(), userId);
    }

    @PostMapping("/{id}/images/confirm")
    @PreAuthorize("hasRole('AGENT')")
    public PropertyResponse confirmImages(
            @PathVariable String id,
            @RequestBody List<String> urls) {
        return propertyService.addImages(id, urls);
    }

    @GetMapping
    public List<PropertyResponse> findAll() {
        return propertyService.findAll(); // Implementar en el service con propertyRepository.findAll()
    }

    @GetMapping("/search")
    public List<PropertyResponse> searchProperties(@RequestParam String term) {
        return propertyService.searchByTerm(term);
    }

    @GetMapping("/{id}")
    public PropertyResponse findById(@PathVariable String id) {
        return propertyService.findById(id);
    }

    @GetMapping("/owner/{ownerId}")
    @PreAuthorize("hasRole('OWNER')")
    public List<PropertyResponse> getByOwner(@PathVariable String ownerId) {
        // Validate the authenticated user is the owner
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String authenticatedUserId = (String) auth.getPrincipal();
        
        if (!authenticatedUserId.equals(ownerId)) {
            throw new SecurityException("You can only view your own properties");
        }
        
        return propertyService.findByOwner(ownerId);
    }

    @PatchMapping("/{id}/assign-owner")
    @PreAuthorize("hasRole('ADMIN')")
    public PropertyResponse assignOwner(
            @PathVariable String id,
            @RequestBody Map<String, String> request,
            @RequestHeader("X-Auth-User-Id") String adminId) {
        String ownerId = request.get("ownerId");
        return propertyService.assignOwner(id, ownerId, adminId);
    }
}