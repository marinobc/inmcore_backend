package com.inmobiliaria.identity_service.service;

import com.inmobiliaria.identity_service.client.NotificationClient;
import com.inmobiliaria.identity_service.domain.UserDocument;
import com.inmobiliaria.identity_service.domain.UserStatus;
import com.inmobiliaria.identity_service.dto.request.ChangePasswordRequest;
import com.inmobiliaria.identity_service.dto.request.LoginRequest;
import com.inmobiliaria.identity_service.dto.request.ResendTempPasswordRequest;
import com.inmobiliaria.identity_service.dto.response.AuthResponse;
import com.inmobiliaria.identity_service.exception.EmailSendException;
import com.inmobiliaria.identity_service.exception.TemporaryPasswordExpiredException;
import com.inmobiliaria.identity_service.exception.UnauthorizedException;
import com.inmobiliaria.identity_service.security.JwtService;
import com.inmobiliaria.identity_service.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RoleClientService roleClientService;
    private final NotificationClient notificationClient;

    public AuthResponse login(LoginRequest request) {
        UserDocument user = userService.findByEmailNormalized(request.email().trim().toLowerCase());

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("User is not active");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        if (Boolean.TRUE.equals(user.getTemporaryPassword())
                && user.getTemporaryPasswordExpiresAt() != null
                && Instant.now().isAfter(user.getTemporaryPasswordExpiresAt())) {
            throw new TemporaryPasswordExpiredException("Temporary password has expired");
        }

        String refreshToken = UUID.randomUUID().toString();
        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        user.setLastLoginAt(Instant.now());
        userService.save(user);

        List<String> roleCodes = roleClientService.resolveRoleCodes(user.getPrimaryRoleIds());

        UserPrincipal principal = new UserPrincipal(
                user.getId(),
                user.getEmailNormalized(),
                roleCodes,
                user.getUserType().name(),
                user.getStatus().name()
        );

        return new AuthResponse(
                jwtService.generateAccessToken(principal),
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds(),
                Boolean.TRUE.equals(user.getMustChangePassword())
        );
    }

    public AuthResponse refresh(String refreshToken) {
        UserDocument user = userService.findByRefreshToken(refreshToken);

        if (user == null || user.getRefreshTokenExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        String newRefreshToken = UUID.randomUUID().toString();
        user.setRefreshToken(newRefreshToken);
        user.setRefreshTokenExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        userService.save(user);

        List<String> roleCodes = roleClientService.resolveRoleCodes(user.getPrimaryRoleIds());

        UserPrincipal principal = new UserPrincipal(
                user.getId(),
                user.getEmailNormalized(),
                roleCodes,
                user.getUserType().name(),
                user.getStatus().name()
        );

        return new AuthResponse(
                jwtService.generateAccessToken(principal),
                newRefreshToken,
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds(),
                Boolean.TRUE.equals(user.getMustChangePassword())
        );
    }

    public void logout(String refreshToken) {
        UserDocument user = userService.findByRefreshToken(refreshToken);
        if (user != null) {
            user.setRefreshToken(null);
            user.setRefreshTokenExpiresAt(null);
            userService.save(user);
        }
    }

    public void changePassword(ChangePasswordRequest request) {
        UserDocument user = userService.findByEmailNormalized(request.email().trim().toLowerCase());

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is invalid");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setTemporaryPassword(false);
        user.setTemporaryPasswordExpiresAt(null);
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(Instant.now());

        userService.save(user);
    }

    public void resendTemporaryPassword(ResendTempPasswordRequest request) {
        UserDocument user = userService.findByEmailNormalized(request.email().trim().toLowerCase());
        
        if (!Boolean.TRUE.equals(user.getTemporaryPassword()) && !Boolean.TRUE.equals(user.getMustChangePassword())) {
            throw new UnauthorizedException("User does not require temporary password reset");
        }
        
        String newTemporaryPassword = userService.generateTemporaryPassword();
        
        user.setPasswordHash(passwordEncoder.encode(newTemporaryPassword));
        user.setTemporaryPassword(true);
        user.setTemporaryPasswordExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(null);
        userService.save(user);
        
        try {
            notificationClient.sendCredentialsEmail(NotificationClient.CredentialsRequest.builder()
                    .to(user.getEmailNormalized())
                    .fullName(user.getFullName())
                    .temporaryPassword(newTemporaryPassword)
                    .build());
        } catch (Exception e) {
            log.error("Failed to resend credentials email to: {}", user.getEmailNormalized(), e);
            throw new EmailSendException("Failed to send email", e);
        }
    }
}