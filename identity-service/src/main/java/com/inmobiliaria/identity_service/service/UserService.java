package com.inmobiliaria.identity_service.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

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
import com.inmobiliaria.identity_service.security.Auditable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final BCryptPasswordEncoder passwordEncoder;
  private final RoleClientService roleClientService;
  private final NotificationClient notificationClient;
  private final UserServiceClient userServiceClient;

  @Auditable(action = "USER_CREATE", description = "New user created")
  public UserResponse create(CreateUserRequest request) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String currentUserId = auth.getName();
    List<String> currentRoles =
        auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(role -> role.replace("ROLE_", ""))
            .toList();

    if (currentRoles.contains("AGENT")) {
      if (request.userType() != UserType.INTERESTED_CLIENT
          && request.userType() != UserType.OWNER) {
        throw new UnauthorizedException("Agents can only create INTERESTED_CLIENT users");
      }
      if (request.assignedAgentId() == null || !request.assignedAgentId().equals(currentUserId)) {
        throw new UnauthorizedException("You can only assign clients to yourself");
      }
    }
    String normalizedEmail = request.email().trim().toLowerCase();

    if (userRepository.existsByEmailNormalized(normalizedEmail)) {
      throw new ResourceAlreadyExistsException("Email already exists: " + normalizedEmail);
    }

    if (request.taxId() != null && !request.taxId().isBlank()) {
      try {
        // Realiza la llamada para verificar si la persona existe por taxId
        Map<String, Object> personData = userServiceClient.getPersonByTaxId(request.taxId());

        // Si no se lanza una excepción, significa que la persona existe.
        // Ahora, necesitamos verificar el estado del usuario en el identity-service.
        String authUserId = (String) personData.get("authUserId");
        if (authUserId != null) {
          UserDocument existingUser = userRepository.findById(authUserId).orElse(null);
          if (existingUser != null) {
            if (existingUser.getStatus() == UserStatus.INACTIVE) {
              throw new ResourceAlreadyExistsException("User inactive.");
            } else {
              throw new ResourceAlreadyExistsException(
                  "A user with CI/NIT " + request.taxId() + " already exists.");
            }
          }
        } else {
          // Si no hay authUserId, podría ser un registro inconsistente, pero aun así,
          // el taxId está en uso.
          throw new ResourceAlreadyExistsException(
              "A user with CI/NIT " + request.taxId() + " already exists.");
        }

      } catch (Exception e) {
        // Si la excepción es porque el recurso no fue encontrado (404),
        // entonces podemos proceder con la creación.
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (!msg.contains("not found") && !msg.contains("404")) {
          // Si es cualquier otra excepción, la relanzamos porque es un error inesperado.
          throw new RuntimeException("Error while verifying user by CI/NIT: " + e.getMessage(), e);
        }
        // Si es "not found" o "404", el flujo continúa y el usuario se crea.
      }
    }
    roleClientService.validateRoleIdsExist(request.roleIds());

    String temporaryPassword = generateTemporaryPassword();

    UserDocument document =
        UserDocument.builder()
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
      userServiceClient.createPerson(
          new CreatePersonRequest(
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
              request.address(),
              request.propertyIds(),
              request.preferredContactMethod(),
              request.budget(),
              request.assignedAgentId(),
              request.preferredZone(),
              request.preferredPropertyType(),
              request.preferredRooms()));
      log.info("Profile created in user-service for authUserId: {}", savedDocument.getId());
    } catch (Exception e) {
      log.error("Failed to propagate profile creation to user-service: {}", e.getMessage());
    }

    if (Boolean.TRUE.equals(request.sendTemporaryCredentials())) {
      sendWelcomeEmail(savedDocument, temporaryPassword);
    }

    return toResponse(savedDocument);
  }

  @Auditable(action = "USER_UPDATE", description = "User information updated")
  public UserResponse update(String id, UpdateUserRequest request) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String currentUserId = auth.getName();
    List<String> currentRoles =
        auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(role -> role.replace("ROLE_", ""))
            .toList();

    if (currentRoles.contains("AGENT")) {
      if (request.assignedAgentId() != null && !request.assignedAgentId().equals(currentUserId)) {
        throw new UnauthorizedException("You can only assign clients to yourself");
      }
    }

    UserDocument user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

    if (request.taxId() != null && !request.taxId().isBlank()) {
      try {
        Map<String, Object> personData = userServiceClient.getPersonByTaxId(request.taxId());
        String authUserId = (String) personData.get("authUserId");
        if (authUserId != null && !authUserId.equals(id)) {
          throw new ResourceAlreadyExistsException(
              "A user with CI/NIT " + request.taxId() + " already exists.");
        }
      } catch (Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (!msg.contains("not found") && !msg.contains("404")) {
          if (e instanceof ResourceAlreadyExistsException) throw e;
          log.warn("Non-critical error verifying taxId: {}", e.getMessage());
        }
      }
    }

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

    UpdatePersonRequest profileUpdate =
        new UpdatePersonRequest(
            request.firstName(),
            request.lastName(),
            request.birthDate(),
            request.phone(),
            request.department(),
            request.position(),
            request.hireDate(),
            request.taxId(),
            request.address(),
            request.propertyIds(),
            request.preferredContactMethod(),
            request.budget(),
            request.preferredZone(),
            request.preferredPropertyType(),
            request.preferredRooms(),
            request.assignedAgentId());

    if (profileUpdate.firstName() != null
        || profileUpdate.lastName() != null
        || profileUpdate.birthDate() != null
        || profileUpdate.phone() != null
        || profileUpdate.department() != null
        || profileUpdate.position() != null
        || profileUpdate.hireDate() != null
        || profileUpdate.taxId() != null
        || profileUpdate.address() != null
        || profileUpdate.propertyIds() != null
        || profileUpdate.preferredContactMethod() != null
        || profileUpdate.budget() != null
        || profileUpdate.preferredZone() != null
        || profileUpdate.preferredPropertyType() != null
        || profileUpdate.preferredRooms() != null
        || profileUpdate.assignedAgentId() != null) {

      try {
        userServiceClient.updatePersonByAuth(id, profileUpdate);
        log.info("Profile updated in user-service for authUserId: {}", id);
      } catch (Exception e) {
        log.error("CRITICAL: Failed to sync update with user-service: {}", e.getMessage());
      }
    }

    return toResponse(user);
  }

  @Auditable(action = "USER_DEACTIVATE", description = "User deactivated")
  public UserResponse deactivate(String id) {
    UserDocument user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

    user.setStatus(UserStatus.INACTIVE);
    user.setUpdatedAt(Instant.now());

    log.info("User {} deactivated (logical delete)", id);
    return toResponse(userRepository.save(user));
  }

  @Auditable(action = "USER_REACTIVATE", description = "User reactivated")
  public UserResponse reactivate(String id) {
    UserDocument user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

    user.setStatus(UserStatus.ACTIVE);
    user.setUpdatedAt(Instant.now());

    log.info("User {} reactivated", id);
    return toResponse(userRepository.save(user));
  }

  @Auditable(action = "USER_DELETE", description = "User permanently deleted")
  public void delete(String id) {
    if (!userRepository.existsById(id)) {
      throw new ResourceNotFoundException("User not found: " + id);
    }

    try {
      userServiceClient.deleteByAuthUserId(id);
    } catch (Exception e) {
      log.warn(
          "Failed to delete profile in user-service, proceeding with auth deletion: {}",
          e.getMessage());
    }

    userRepository.deleteById(id);
  }

  @Auditable(action = "USER_ROLE_ASSIGN", description = "User roles assigned/updated")
  public UserResponse assignRole(String userId, AssignRoleRequest request) {
    UserDocument user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

    roleClientService.validateRoleIdsExist(request.roleIds());
    user.setPrimaryRoleIds(request.roleIds());
    user.setUpdatedAt(Instant.now());

    return toResponse(userRepository.save(user));
  }

  private void sendWelcomeEmail(UserDocument user, String password) {
    try {
      notificationClient.sendCredentialsEmail(
          NotificationClient.CredentialsRequest.builder()
              .to(user.getEmailNormalized())
              .fullName(user.getFullName())
              .temporaryPassword(password)
              .build());
    } catch (Exception e) {
      log.error("Failed to send credentials email: {}", e.getMessage());
    }
  }

  public Page<UserResponse> findAll(UserStatus status, String query, Pageable pageable) {
    String safeQuery = query != null ? query.trim() : "";
    return userRepository.findAllFiltered(status, safeQuery, pageable).map(this::toResponse);
  }

  public UserResponse findById(String id) {
    return userRepository
        .findById(id)
        .map(this::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
  }

  public UserDocument findByEmailNormalized(String emailNormalized) {
    return userRepository
        .findByEmailNormalized(emailNormalized)
        .orElseThrow(
            () -> new ResourceNotFoundException("User not found with email: " + emailNormalized));
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
        document.getPrimaryRoleIds());
  }
}
