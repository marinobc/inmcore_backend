package com.inmobiliaria.property_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Set;

/**
 * DTO para la creación de un nuevo inmueble.
 * Contiene las validaciones necesarias para las pruebas de aceptación.
 */
public record PropertyRequest(
        @NotBlank(message = "El título es obligatorio para el inventario")
        String title,

        @NotBlank(message = "La dirección es requerida para el registro")
        String address,

        @NotNull(message = "El precio no puede estar vacío")
        @Positive(message = "El precio debe ser un valor mayor a cero")
        Double price,

        @NotBlank(message = "Debe especificar el tipo de inmueble (Apartamento, Casa, etc.)")
        String type,

        @NotNull(message = "Los metros cuadrados (m2) son obligatorios")
        @Positive(message = "El área debe ser un número positivo")
        Double m2,

        @NotNull(message = "El número de habitaciones es obligatorio")
        @Min(value = 0, message = "El número de habitaciones no puede ser negativo")
        Integer rooms,

        Set<String> accessPolicy,

        String ownerId
) {}