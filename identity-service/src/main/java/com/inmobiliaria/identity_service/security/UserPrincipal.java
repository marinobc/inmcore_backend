package com.inmobiliaria.identity_service.security;

import java.util.List;

public record UserPrincipal(
        String userId,
        String email,
        List<String> roleIds,
        String userType,
        String status
) {
}