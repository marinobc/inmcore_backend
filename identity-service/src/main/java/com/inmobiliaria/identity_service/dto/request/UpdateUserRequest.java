package com.inmobiliaria.identity_service.dto.request;

import com.inmobiliaria.identity_service.domain.UserType;
import java.time.LocalDate;

public record UpdateUserRequest(
        String firstName,
        String lastName,
        UserType userType,
        LocalDate birthDate,
        String phone,
        String department,
        String position,
        LocalDate hireDate,
        String taxId,
        String preferredContactMethod,
        String budget
) {
}