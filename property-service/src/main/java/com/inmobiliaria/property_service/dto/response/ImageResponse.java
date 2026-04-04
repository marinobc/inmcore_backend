package com.inmobiliaria.property_service.dto.response;

import com.inmobiliaria.property_service.domain.ImageMetadata;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;

@Data
@Builder
public class ImageResponse {
    private String id;
    private String originalFileName;
    private String objectKey;
    private String publicUrl;
    private Long fileSize;
    private String mimeType;
    private Integer width;
    private Integer height;
    private Boolean isPrimary;
    private Integer displayOrder;
    private Instant uploadedAt;
    private String uploadedBy;
    private String uploadedByName;
    private ImageMetadata.ImageStatus status;
    private Set<String> accessPolicy;
    private String temporaryDownloadUrl;
    private Integer expiresInSeconds;
}