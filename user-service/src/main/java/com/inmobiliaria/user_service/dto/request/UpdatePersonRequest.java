package com.inmobiliaria.user_service.dto.request;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record UpdatePersonRequest(
        String firstName,
        String lastName,
        LocalDate birthDate,
        String phone,
        String department,
        String position,
        Instant hireDate,
        String taxId,
        String address,
        List<String> propertyIds,
        String preferredContactMethod,
        String budget
) {}