package com.inmobiliaria.identity_service.service;

import com.inmobiliaria.identity_service.client.NotificationClient;
import com.inmobiliaria.identity_service.client.UserServiceClient;
import com.inmobiliaria.identity_service.client.dto.CreatePersonRequest;
import com.inmobiliaria.identity_service.client.dto.PersonType;
import com.inmobiliaria.identity_service.client.dto.UpdatePersonRequest;
import com.inmobiliaria.identity_service.domain.UserDocument;
import com.inmobiliaria.identity_service.domain.UserStatus;
import com.inmobiliaria.identity_service.domain.UserType;
import com.inmobiliaria.identity_service.dto.request.AssignRoleRequest;
import com.inmobiliaria.identity_service.dto.request.CreateUserRequest;
import com.inmobiliaria.identity_service.dto.request.UpdateUserRequest;
import com.inmobiliaria.identity_service.dto.response.UserResponse;
import com.inmobiliaria.identity_service.exception.ResourceAlreadyExistsException;
import com.inmobiliaria.identity_service.exception.ResourceNotFoundException;
import com.inmobiliaria.identity_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RoleClientService roleClientService;
    private final NotificationClient notificationClient;
    private final UserServiceClient userServiceClient;

    public UserResponse create(CreateUserRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailNormalized(normalizedEmail)) {
            throw new ResourceAlreadyExistsException("Email already exists: " + normalizedEmail);
        }

        roleClientService.validateRoleIdsExist(request.roleIds());

        String temporaryPassword = generateTemporaryPassword();

        UserDocument document = UserDocument.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .fullName(request.firstName().trim() + " " + request.lastName().trim())
                .email(request.email().trim())
                .emailNormalized(normalizedEmail)
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .userType(request.userType())
                .status(UserStatus.ACTIVE)
                .temporaryPassword(true)
                .temporaryPasswordExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .mustChangePassword(true)
                .passwordChangedAt(null)
                .failedLoginAttempts(0)
                .lockedUntil(null)
                .lastLoginAt(null)
                .primaryRoleIds(request.roleIds())
                .activeEmploymentCycleId(null)
                .metadata(Map.of("temporaryPasswordPlain", temporaryPassword))
                .build();

        document.setCreatedAt(Instant.now());
        document.setUpdatedAt(Instant.now());
        document.setCreatedBy("system");

        UserDocument savedDocument = userRepository.save(document);

        try {
            userServiceClient.createPerson(new CreatePersonRequest(
                    savedDocument.getId(),
                    savedDocument.getFirstName(),
                    savedDocument.getLastName(),
                    request.birthDate(),
                    request.phone(),
                    savedDocument.getEmail(),
                    mapToPersonType(savedDocument.getUserType()),
                    savedDocument.getPrimaryRoleIds(),
                    null, null, null, null, null, null
            ));
        } catch (Exception e) {
            log.error("Failed to create person profile in user-service for authUserId: {}", savedDocument.getId(), e);
        }

        if (Boolean.TRUE.equals(request.sendTemporaryCredentials())) {
            try {
                notificationClient.sendCredentialsEmail(NotificationClient.CredentialsRequest.builder()
                        .to(savedDocument.getEmailNormalized())
                        .fullName(savedDocument.getFullName())
                        .temporaryPassword(temporaryPassword)
                        .build());
            } catch (Exception e) {
                log.error("Failed to send credentials email to: {}", savedDocument.getEmailNormalized(), e);
            }
        }

        return toResponse(savedDocument);
    }

    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public UserResponse findById(String id) {
        UserDocument user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return toResponse(user);
    }

    public UserResponse update(String id, UpdateUserRequest request) {
        UserDocument user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        if (request.firstName() != null) user.setFirstName(request.firstName().trim());
        if (request.lastName() != null) user.setLastName(request.lastName().trim());
        user.setFullName(user.getFirstName() + " " + user.getLastName());
        if (request.userType() != null) user.setUserType(request.userType());

        user.setUpdatedAt(Instant.now());
        UserDocument saved = userRepository.save(user);

        try {
            userServiceClient.updatePerson(id, new UpdatePersonRequest(
                    request.firstName(),
                    request.lastName(),
                    request.birthDate(),
                    request.phone(),
                    null, null, null, null, null, null
            ));
        } catch (Exception e) {
            log.error("Failed to update person profile in user-service for authUserId: {}", id, e);
        }

        return toResponse(saved);
    }

    public void delete(String id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);

        try {
            userServiceClient.deleteByAuthUserId(id);
        } catch (Exception e) {
            log.warn("Failed to delete person profile in user-service for authUserId: {}", id, e);
        }
    }

    public UserResponse assignRole(String userId, AssignRoleRequest request) {
        UserDocument user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        roleClientService.validateRoleIdsExist(request.roleIds());

        user.setPrimaryRoleIds(request.roleIds());
        user.setUpdatedAt(Instant.now());

        return toResponse(userRepository.save(user));
    }

    public UserDocument findByEmailNormalized(String emailNormalized) {
        return userRepository.findByEmailNormalized(emailNormalized)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + emailNormalized));
    }

    public UserDocument findByRefreshToken(String refreshToken) {
        return userRepository.findByRefreshToken(refreshToken)
                .orElse(null);
    }

    public UserDocument save(UserDocument userDocument) {
        userDocument.setUpdatedAt(Instant.now());
        return userRepository.save(userDocument);
    }

    private String generateTemporaryPassword() {
        return "Tmp-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private PersonType mapToPersonType(UserType userType) {
        return switch (userType) {
            case ADMIN -> PersonType.ADMIN;
            case EMPLOYEE -> PersonType.EMPLOYEE;
            case OWNER -> PersonType.OWNER;
            case INTERESTED_CLIENT -> PersonType.INTERESTED_CLIENT;
        };
    }

    private UserResponse toResponse(UserDocument document) {
        return new UserResponse(
                document.getId(),
                document.getFirstName(),
                document.getLastName(),
                document.getFullName(),
                document.getEmail(),
                document.getUserType(),
                document.getStatus(),
                document.getTemporaryPassword(),
                document.getTemporaryPasswordExpiresAt(),
                document.getMustChangePassword(),
                document.getPrimaryRoleIds()
        );
    }
}