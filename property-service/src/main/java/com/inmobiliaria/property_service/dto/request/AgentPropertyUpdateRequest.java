package com.inmobiliaria.property_service.dto.request;

import com.inmobiliaria.property_service.domain.OperationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AgentPropertyUpdateRequest(
        @NotBlank(message = "El título es obligatorio")
        String title,

        @NotBlank(message = "La dirección es requerida")
        String address,

        @NotBlank(message = "Debe especificar el tipo de inmueble")
        String type,

        @NotNull(message = "Los metros cuadrados son obligatorios")
        @Positive(message = "El área debe ser un número positivo")
        Double m2,

        @NotNull(message = "El número de habitaciones es obligatorio")
        @Min(value = 0, message = "El número de habitaciones no puede ser negativo")
        Integer rooms,

        @NotNull(message = "El tipo de operación es obligatorio")
        OperationType operationType,

        String ownerId
) {}