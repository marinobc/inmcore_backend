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
import com.inmobiliaria.identity_service.exception.UnauthorizedException;
import com.inmobiliaria.identity_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
        // Obtener el usuario autenticado desde el contexto de seguridad
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUserId = auth.getName();
        List<String> currentRoles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(role -> role.replace("ROLE_", ""))
                .toList();

        // Si es un agente (tiene rol AGENT)
        if (currentRoles.contains("AGENT")) {
            // Solo puede crear clientes interesados
            if (request.userType() != UserType.INTERESTED_CLIENT) {
                throw new UnauthorizedException("Agents can only create INTERESTED_CLIENT users");
            }
            // El assignedAgentId debe coincidir con el ID del agente autenticado
            if (request.assignedAgentId() == null || !request.assignedAgentId().equals(currentUserId)) {
                throw new UnauthorizedException("You can only assign clients to yourself");
            }
        }
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
                .temporaryPasswordExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .mustChangePassword(true)
                .primaryRoleIds(request.roleIds())
                .metadata(Map.of("createdVia", "admin_panel"))
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
                    request.department(),
                    request.position(),
                    request.hireDate(),
                    request.taxId(),
                    null,
                    null,
                    request.preferredContactMethod(),
                    request.budget(),
                    request.assignedAgentId(),
                    request.preferredZone(),
                    request.preferredPropertyType(),
                    request.preferredRooms()  
            ));
            log.info("Profile created in user-service for authUserId: {}", savedDocument.getId());
        } catch (Exception e) {
            log.error("Failed to propagate profile creation to user-service: {}", e.getMessage());
        }

        if (Boolean.TRUE.equals(request.sendTemporaryCredentials())) {
            sendWelcomeEmail(savedDocument, temporaryPassword);
        }

        return toResponse(savedDocument);
    }


    public UserResponse update(String id, UpdateUserRequest request) {
        UserDocument user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        boolean identityUpdated = false;

        if (request.firstName() != null) {
            user.setFirstName(request.firstName().trim());
            identityUpdated = true;
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName().trim());
            identityUpdated = true;
        }
        if (request.userType() != null) {
            user.setUserType(request.userType());
            identityUpdated = true;
        }

        if (identityUpdated) {
            user.setFullName(user.getFirstName() + " " + user.getLastName());
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);
        }

        UpdatePersonRequest profileUpdate = new UpdatePersonRequest(
                request.firstName(),
                request.lastName(),
                request.birthDate(),
                request.phone(),
                request.department(),
                request.position(),
                request.hireDate(),
                request.taxId(),
                request.preferredContactMethod(),
                request.budget()
        );

        if (profileUpdate.firstName() != null ||
            profileUpdate.lastName() != null ||
            profileUpdate.birthDate() != null ||
            profileUpdate.phone() != null ||
            profileUpdate.department() != null ||
            profileUpdate.position() != null ||
            profileUpdate.hireDate() != null ||
            profileUpdate.taxId() != null ||
            profileUpdate.preferredContactMethod() != null ||
            profileUpdate.budget() != null) {

            try {
                userServiceClient.updatePersonByAuth(id, profileUpdate);
                log.info("Profile updated in user-service for authUserId: {}", id);
            } catch (Exception e) {
                log.error("CRITICAL: Failed to sync update with user-service: {}", e.getMessage());
            }
        }

        return toResponse(user);
    }
    
    public UserResponse deactivate(String id) {
        UserDocument user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        user.setStatus(UserStatus.INACTIVE);
        user.setUpdatedAt(Instant.now());

        log.info("User {} deactivated (logical delete)", id);
        return toResponse(userRepository.save(user));
    }

    public UserResponse reactivate(String id) {
        UserDocument user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        user.setStatus(UserStatus.ACTIVE);
        user.setUpdatedAt(Instant.now());

        log.info("User {} reactivated", id);
        return toResponse(userRepository.save(user));
    }

    public void delete(String id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }

        try {
            userServiceClient.deleteByAuthUserId(id);
        } catch (Exception e) {
            log.warn("Failed to delete profile in user-service, proceeding with auth deletion: {}", e.getMessage());
        }

        userRepository.deleteById(id);
    }

    public UserResponse assignRole(String userId, AssignRoleRequest request) {
        UserDocument user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        roleClientService.validateRoleIdsExist(request.roleIds());
        user.setPrimaryRoleIds(request.roleIds());
        user.setUpdatedAt(Instant.now());

        return toResponse(userRepository.save(user));
    }

    private void sendWelcomeEmail(UserDocument user, String password) {
        try {
            notificationClient.sendCredentialsEmail(NotificationClient.CredentialsRequest.builder()
                    .to(user.getEmailNormalized())
                    .fullName(user.getFullName())
                    .temporaryPassword(password)
                    .build());
        } catch (Exception e) {
            log.error("Failed to send credentials email: {}", e.getMessage());
        }
    }

    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public UserResponse findById(String id) {
        return userRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    public UserDocument findByEmailNormalized(String emailNormalized) {
        return userRepository.findByEmailNormalized(emailNormalized)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + emailNormalized));
    }

    public UserDocument findByRefreshToken(String refreshToken) {
        return userRepository.findByRefreshToken(refreshToken).orElse(null);
    }

    public UserDocument save(UserDocument userDocument) {
        userDocument.setUpdatedAt(Instant.now());
        return userRepository.save(userDocument);
    }

    public String generateTemporaryPassword() {
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