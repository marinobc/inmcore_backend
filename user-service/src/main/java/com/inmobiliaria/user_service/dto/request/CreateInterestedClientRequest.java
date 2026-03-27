package com.inmobiliaria.user_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateInterestedClientRequest(
        @NotBlank String authUserId,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull LocalDate birthDate,
        @NotBlank String phone,
        @NotBlank String email,
        String preferredContactMethod,
        String budget
) {}
