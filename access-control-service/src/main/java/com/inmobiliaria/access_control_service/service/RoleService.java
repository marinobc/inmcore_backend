package com.inmobiliaria.access_control_service.service;

import com.inmobiliaria.access_control_service.domain.PermissionEntry;
import com.inmobiliaria.access_control_service.domain.RoleDocument;
import com.inmobiliaria.access_control_service.domain.RoleType;
import com.inmobiliaria.access_control_service.dto.request.CreateRoleRequest;
import com.inmobiliaria.access_control_service.dto.request.PermissionRequest;
import com.inmobiliaria.access_control_service.dto.request.UpdateRoleRequest;
import com.inmobiliaria.access_control_service.dto.response.RoleResponse;
import com.inmobiliaria.access_control_service.exception.ResourceAlreadyExistsException;
import com.inmobiliaria.access_control_service.exception.ResourceNotFoundException;
import com.inmobiliaria.access_control_service.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionCatalogService permissionCatalogService;

    @Cacheable("roles")
    public List<RoleResponse> findAll() {
        return roleRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public RoleResponse findById(String id) {
        return toResponse(findDocumentById(id));
    }

    @CacheEvict(value = "roles", allEntries = true)
    public RoleResponse create(CreateRoleRequest request) {
        if (roleRepository.existsByCode(request.code())) {
            throw new ResourceAlreadyExistsException("Role code already exists: " + request.code());
        }

        if (roleRepository.existsByName(request.name())) {
            throw new ResourceAlreadyExistsException("Role name already exists: " + request.name());
        }

        List<PermissionEntry> permissions = mapPermissions(request.permissions());
        permissionCatalogService.validatePermissionsExist(permissions);

        RoleDocument document = RoleDocument.builder()
                .code(request.code().trim().toUpperCase())
                .name(request.name().trim())
                .description(request.description().trim())
                .type(RoleType.CUSTOM)
                .active(true)
                .permissions(permissions)
                .version(1)
                .build();

        document.setCreatedAt(Instant.now());
        document.setUpdatedAt(Instant.now());
        document.setCreatedBy("system");

        return toResponse(roleRepository.save(document));
    }

    @CacheEvict(value = "roles", allEntries = true)
    public RoleResponse update(String id, UpdateRoleRequest request) {
        RoleDocument existing = findDocumentById(id);

        List<PermissionEntry> permissions = mapPermissions(request.permissions());
        permissionCatalogService.validatePermissionsExist(permissions);

        existing.setName(request.name().trim());
        existing.setDescription(request.description().trim());
        existing.setPermissions(permissions);
        existing.setActive(request.active() != null ? request.active() : existing.getActive());
        existing.setVersion(existing.getVersion() + 1);
        existing.setUpdatedAt(Instant.now());

        return toResponse(roleRepository.save(existing));
    }

    private RoleDocument findDocumentById(String id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
    }

    private List<PermissionEntry> mapPermissions(List<PermissionRequest> permissions) {
        return permissions.stream()
                .map(permission -> PermissionEntry.builder()
                        .resource(permission.resource().trim())
                        .action(permission.action().trim())
                        .scope(permission.scope())
                        .build())
                .toList();
    }

    private RoleResponse toResponse(RoleDocument document) {
        return new RoleResponse(
                document.getId(),
                document.getCode(),
                document.getName(),
                document.getDescription(),
                document.getType(),
                document.getActive(),
                document.getPermissions(),
                document.getVersion()
        );
    }
}