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
public class ImageMetadata {
    
    private String id;
    private String originalFileName;
    private String objectKey;
    private String publicUrl;
    private Long fileSize;
    private String mimeType;
    private Integer width;
    private Integer height;
    private Boolean isPrimary;        // Main property image
    private Integer displayOrder;      // Order in gallery
    private Instant uploadedAt;
    private String uploadedBy;
    private String uploadedByName;
    private ImageStatus status;
    
    @Builder.Default
    private Set<String> accessPolicy = new HashSet<>(); // For sensitive images
    
    public enum ImageStatus {
        ACTIVE,
        HIDDEN,    // Admin hidden but not deleted
        PROCESSING  // Being processed/optimized
    }
}