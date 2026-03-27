package com.inmobiliaria.identity_service.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDocument extends BaseDocument {

    @Id
    private String id;

    private String firstName;
    private String lastName;
    private String fullName;

    private String email;

    @Indexed(unique = true)
    private String emailNormalized;

    private String passwordHash;
    private UserType userType;
    private UserStatus status;

    private Boolean temporaryPassword;
    private Instant temporaryPasswordExpiresAt;
    private Boolean mustChangePassword;

    private Instant passwordChangedAt;
    private Integer failedLoginAttempts;
    private Instant lockedUntil;
    private Instant lastLoginAt;

    private List<String> primaryRoleIds;

    @Indexed
    private String activeEmploymentCycleId;

    private Map<String, Object> metadata;

    private String refreshToken;
    private Instant refreshTokenExpiresAt;
}