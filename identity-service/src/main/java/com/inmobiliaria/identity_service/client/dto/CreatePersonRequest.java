package com.inmobiliaria.identity_service.client.dto;

import java.time.Instant;
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
        
        // Optional type-specific fields
        String department,
        String position,
        Instant hireDate,
        String taxId,
        String preferredContactMethod,
        String budget
) {}
