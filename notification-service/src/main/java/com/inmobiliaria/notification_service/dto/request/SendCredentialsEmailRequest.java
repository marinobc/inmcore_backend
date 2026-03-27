package com.inmobiliaria.notification_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendCredentialsEmailRequest(
        @Email @NotBlank String to,
        @NotBlank String fullName,
        @NotBlank String temporaryPassword
) {
}