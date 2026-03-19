package com.inmobiliaria.identity_service.dto.request;

import com.inmobiliaria.identity_service.domain.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateUserRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Email @NotBlank String email,
        @NotNull UserType userType,
        @NotEmpty List<String> roleIds,
        @NotNull java.time.LocalDate birthDate,
        @NotBlank String phone,
        Boolean sendTemporaryCredentials
) {
}