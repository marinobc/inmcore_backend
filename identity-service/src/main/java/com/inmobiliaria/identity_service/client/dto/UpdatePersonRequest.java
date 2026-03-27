package com.inmobiliaria.identity_service.client.dto;

import java.time.LocalDate;

public record UpdatePersonRequest(
        String firstName,
        String lastName,
        LocalDate birthDate,
        String phone,
        String department,
        String position,
        LocalDate hireDate,
        String taxId,
        String preferredContactMethod,
        String budget
) {}
