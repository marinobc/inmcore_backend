package com.inmobiliaria.access_control_service.dto.response;

import com.inmobiliaria.access_control_service.domain.PermissionEntry;
import com.inmobiliaria.access_control_service.domain.RoleType;

import java.util.List;

public record RoleResponse(
        String id,
        String code,
        String name,
        String description,
        RoleType type,
        Boolean active,
        List<PermissionEntry> permissions,
        Integer version
) {
}