// REEMPLAZAR el archivo completo:
package com.inmobiliaria.user_service.dto.request;

import java.time.LocalDate;
import java.util.List;

public record UpdatePersonRequest(
        String firstName,
        String lastName,
        LocalDate birthDate,
        String phone,
        String department,
        String position,
        LocalDate hireDate,
        String taxId,
        String address,
        List<String> propertyIds,
        String preferredContactMethod,
        String budget,
        // Campos nuevos
        String preferredZone,
        String preferredPropertyType,
        Integer preferredRooms
) {}