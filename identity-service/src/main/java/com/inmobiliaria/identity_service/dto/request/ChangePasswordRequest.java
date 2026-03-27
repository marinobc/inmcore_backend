package com.inmobiliaria.identity_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String email,
        @NotBlank String currentPassword,
        @NotBlank String newPassword
) {
}