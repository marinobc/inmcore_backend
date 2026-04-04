package com.inmobiliaria.property_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class GenerateUploadUrlRequest {
    
    @NotBlank(message = "Property ID is required")
    private String propertyId;
    
    @NotBlank(message = "Document type is required")
    private String documentType; // "EXCLUSIVITY_CONTRACT"
    
    @NotBlank(message = "File name is required")
    private String fileName;
    
    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    private Long fileSize;
    
    private String mimeType;
}