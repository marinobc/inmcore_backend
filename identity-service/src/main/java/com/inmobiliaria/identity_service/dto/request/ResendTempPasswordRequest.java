package com.inmobiliaria.identity_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendTempPasswordRequest(
        @Email @NotBlank String email
) {}