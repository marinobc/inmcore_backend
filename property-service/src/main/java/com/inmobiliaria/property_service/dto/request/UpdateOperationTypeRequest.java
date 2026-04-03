package com.inmobiliaria.property_service.dto.request;

import com.inmobiliaria.property_service.domain.OperationType;
import jakarta.validation.constraints.NotNull;

public record UpdateOperationTypeRequest(
    @NotNull(message = "El tipo de operación es obligatorio") 
    OperationType operationType
) {}