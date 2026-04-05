package com.inmobiliaria.property_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateStatusRequest(
    @NotBlank(message = "El nuevo estado es obligatorio") 
    String status
) {}