package com.inmobiliaria.property_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmUploadRequest {
    
    @NotBlank(message = "Property ID is required")
    private String propertyId;
    
    @NotBlank(message = "Document type is required")
    private String documentType;
    
    @NotBlank(message = "Object key is required")
    private String objectKey;
    
    @NotBlank(message = "Original file name is required")
    private String originalFileName;
    
    private Long fileSize;
    private String mimeType;
}