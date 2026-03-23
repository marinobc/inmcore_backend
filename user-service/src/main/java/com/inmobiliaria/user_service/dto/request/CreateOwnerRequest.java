package com.inmobiliaria.user_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record CreateOwnerRequest(
        @NotBlank String authUserId,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull  LocalDate birthDate,
        @NotBlank String phone,
        @NotBlank @Email String email,
        @NotBlank String taxId,
        @NotBlank String address,
        List<String> propertyIds 
) {}