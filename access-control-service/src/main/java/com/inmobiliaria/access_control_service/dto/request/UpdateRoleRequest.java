package com.inmobiliaria.access_control_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateRoleRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotEmpty List<@Valid PermissionRequest> permissions,
        Boolean active
) {
}