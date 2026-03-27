// REEMPLAZAR el archivo completo:
package com.inmobiliaria.identity_service.client.dto;

import java.time.LocalDate;
import java.util.List;

public record CreatePersonRequest(
        String authUserId,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String phone,
        String email,
        PersonType personType,
        List<String> roleIds,

        // Employee-specific
        String department,
        String position,
        LocalDate hireDate,

        // Owner-specific
        String taxId,
        String address,
        List<String> propertyIds,

        // InterestedClient-specific
        String preferredContactMethod,
        String budget,

        // ID del agente que crea este cliente
        String assignedAgentId,

        // Preferencias nuevas de cliente
        String preferredZone,
        String preferredPropertyType,
        Integer preferredRooms
) {}