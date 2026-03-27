package com.inmobiliaria.user_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateEmployeeRequest(
        @NotBlank String authUserId,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull LocalDate birthDate,
        @NotBlank String phone,
        @NotBlank String email,
        @NotBlank String department,
        @NotBlank String position,
        LocalDate hireDate
) {}
