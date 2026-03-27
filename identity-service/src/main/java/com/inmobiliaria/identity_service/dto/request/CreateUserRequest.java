// REEMPLAZAR el archivo completo:
package com.inmobiliaria.identity_service.dto.request;

import com.inmobiliaria.identity_service.domain.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateUserRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Email @NotBlank String email,
        @NotNull UserType userType,
        @NotEmpty List<String> roleIds,
        @NotNull LocalDate birthDate,
        @NotBlank String phone,
        Boolean sendTemporaryCredentials,

        // Employee-specific
        String department,
        String position,
        LocalDate hireDate,

        // Owner-specific
        String taxId,

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