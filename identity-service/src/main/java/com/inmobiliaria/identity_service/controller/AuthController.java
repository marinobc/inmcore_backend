package com.inmobiliaria.identity_service.controller;

import com.inmobiliaria.identity_service.dto.request.ChangePasswordRequest;
import com.inmobiliaria.identity_service.dto.request.LoginRequest;
import com.inmobiliaria.identity_service.dto.request.RefreshTokenRequest;
import com.inmobiliaria.identity_service.dto.request.ResendTempPasswordRequest;
import com.inmobiliaria.identity_service.dto.response.AuthResponse;
import com.inmobiliaria.identity_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/resend-temp-password")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    public void resendTemporaryPassword(@Valid @RequestBody ResendTempPasswordRequest request) {
        authService.resendTemporaryPassword(request);
    }
}