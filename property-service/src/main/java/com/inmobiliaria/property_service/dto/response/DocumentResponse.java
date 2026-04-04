package com.inmobiliaria.property_service.dto.response;

import com.inmobiliaria.property_service.domain.DocumentMetadata;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;

@Data
@Builder
public class DocumentResponse {
    private String id;
    private String documentType;
    private String originalFileName;
    private String objectKey;
    private String publicUrl;
    private Long fileSize;
    private String mimeType;
    private Instant uploadedAt;
    private String uploadedBy;
    private String uploadedByName;
    private DocumentMetadata.DocumentStatus status;
    private Set<String> accessPolicy;
    private Instant validUntil;
    private String temporaryDownloadUrl; // Presigned URL for download (temporary)
    private Integer expiresInSeconds;    // Expiration time for the URL
}