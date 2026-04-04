package com.inmobiliaria.property_service.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImageUploadResponse {
    private String fileName;
    private String uploadUrl;
    private String publicUrl;
    private String objectKey;
}