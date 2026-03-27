package com.inmobiliaria.identity_service.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        boolean mustChangePassword
) {
}