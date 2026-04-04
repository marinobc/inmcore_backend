package com.inmobiliaria.property_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateDocumentPermissionsRequest {
    
    @NotBlank(message = "Document ID is required")
    private String documentId;
    
    @NotNull(message = "Access policy is required")
    private Set<String> accessPolicy; // Roles (ROLE_ADMIN, ROLE_AGENT) or user IDs
}