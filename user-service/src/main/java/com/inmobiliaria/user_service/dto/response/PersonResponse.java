package com.inmobiliaria.user_service.dto.response;

import com.inmobiliaria.user_service.domain.PersonType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PersonResponse(
        String id,
        String authUserId,
        String firstName,
        String lastName,
        String fullName,
        LocalDate birthDate,
        String phone,
        String email,
        PersonType personType,
        List<String> roleIds,
        boolean customRole,

        // Employee-specific
        String department,
        String position,
        Instant hireDate,

        // Owner-specific
        String taxId,
        String address,
        List<String> propertyIds,

        // InterestedClient-specific
        String preferredContactMethod,
        String budget
) {}