package com.inmobiliaria.property_service.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record AccessPolicyRequest(
        @NotNull(message = "La política de acceso no puede ser nula")
        Set<String> accessPolicy
) {}
