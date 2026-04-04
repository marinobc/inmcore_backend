package com.inmobiliaria.property_service.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadata {
    
    private String id;
    private String documentType;      // "EXCLUSIVITY_CONTRACT", "ID_DOCUMENT", etc.
    private String originalFileName;
    private String objectKey;          // MinIO object key
    private String publicUrl;          // Permanent public URL (for reference)
    private Long fileSize;
    private String mimeType;
    private Instant uploadedAt;
    private String uploadedBy;         // Agent ID who uploaded
    private String uploadedByName;
    private DocumentStatus status;     // PENDING, ACTIVE, EXPIRED, REJECTED
    
    @Builder.Default
    private Set<String> accessPolicy = new HashSet<>(); // Roles or user IDs with access
    
    private Instant validUntil;        // For time-limited documents
    
    public enum DocumentStatus {
        PENDING,    // Uploaded but not yet validated
        ACTIVE,     // Valid and active
        EXPIRED,    // Contract expired
        REJECTED    // Rejected by admin
    }
    
    public enum DocumentType {
    EXCLUSIVITY_CONTRACT,  // PDF
    PROPERTY_IMAGE,        // JPG, PNG, WebP
    ID_DOCUMENT,          // PDF/Image
    // ... others
    }
}