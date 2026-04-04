package com.inmobiliaria.property_service.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record AssignOwnerRequest(
    @NotBlank 
    @JsonProperty("ownerId") // Forzar el mapeo desde el JSON
    String ownerId
) {}