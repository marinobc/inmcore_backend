package com.inmobiliaria.access_control_service.dto.response;

import com.inmobiliaria.access_control_service.domain.ScopeType;

public record PermissionCatalogResponse(
        String id,
        String resource,
        String action,
        ScopeType scope,
        String description,
        Boolean active
) {
}