package com.inmobiliaria.user_service.dto.request;

import com.inmobiliaria.user_service.domain.PersonType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CreatePersonRequest(
        @NotBlank String authUserId,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull  LocalDate birthDate,
        @NotBlank String phone,
        @NotBlank @Email String email,
        @NotNull  PersonType personType,
        List<String> roleIds,

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