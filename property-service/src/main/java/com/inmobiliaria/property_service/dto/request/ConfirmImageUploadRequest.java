package com.inmobiliaria.property_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmImageUploadRequest {
    
    @NotBlank(message = "Object key is required")
    private String objectKey;
    
    private String originalFileName;
    private Long fileSize;
    private String mimeType;
    private Boolean isPrimary;
}