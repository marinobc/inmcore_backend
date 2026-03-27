package com.inmobiliaria.user_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CreateOwnerRequest(
        @NotBlank String authUserId,

        @NotBlank
        @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
        String firstName,

        @NotBlank
        @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
        String lastName,

        @NotNull(message = "Birth date is required")
        LocalDate birthDate,

        @NotBlank
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid phone number format")
        String phone,

        @NotBlank
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Tax ID (CI) is required")
        @Pattern(regexp = "^[0-9]{7,10}$", message = "CI must be between 7 and 10 digits")
        String taxId,

        @NotBlank(message = "Address is required")
        @Size(min = 5, max = 200, message = "Address must be between 5 and 200 characters")
        String address,

        List<String> propertyIds
) {}