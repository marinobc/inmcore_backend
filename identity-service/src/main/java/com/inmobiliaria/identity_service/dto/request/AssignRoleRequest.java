package com.inmobiliaria.identity_service.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AssignRoleRequest(
        @NotEmpty List<String> roleIds
) {
}