package com.inmobiliaria.identity_service.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.inmobiliaria.identity_service.dto.request.AssignRoleRequest;
import com.inmobiliaria.identity_service.dto.request.CreateUserRequest;
import com.inmobiliaria.identity_service.dto.request.UpdateUserRequest;
import com.inmobiliaria.identity_service.dto.response.ApiResponse;
import com.inmobiliaria.identity_service.dto.response.ResponseFactory;
import com.inmobiliaria.identity_service.dto.response.UserResponse;
import com.inmobiliaria.identity_service.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final ResponseFactory responseFactory;

  @PostMapping
  @PreAuthorize("hasRole('ADMIN') or hasRole('AGENT')")
  public ResponseEntity<ApiResponse<UserResponse>> create(
      @Valid @RequestBody CreateUserRequest request) {
    UserResponse response = userService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(responseFactory.created("User created successfully", response));
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN') or hasRole('AGENT')")
  public ResponseEntity<ApiResponse<List<UserResponse>>> findAll(
      @RequestParam(required = false) com.inmobiliaria.identity_service.domain.UserStatus status,
      @RequestParam(required = false) String query,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int pageSize) {
    Page<UserResponse> usersPage =
        userService.findAll(status, query, PageRequest.of(page, pageSize));
    return ResponseEntity.ok(responseFactory.paginated("Users retrieved successfully", usersPage));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') or hasRole('AGENT')")
  public ResponseEntity<ApiResponse<UserResponse>> findById(@PathVariable String id) {
    UserResponse response = userService.findById(id);
    return ResponseEntity.ok(responseFactory.success("User retrieved successfully", response));
  }

  @PutMapping("/{id}/role")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<UserResponse>> assignRole(
      @PathVariable String id, @Valid @RequestBody AssignRoleRequest request) {
    UserResponse response = userService.assignRole(id, request);
    return ResponseEntity.ok(responseFactory.success("Role assigned successfully", response));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') or hasRole('AGENT')")
  public ResponseEntity<ApiResponse<UserResponse>> update(
      @PathVariable String id, @Valid @RequestBody UpdateUserRequest request) {
    UserResponse response = userService.update(id, request);
    return ResponseEntity.ok(responseFactory.success("User updated successfully", response));
  }

  @PutMapping("/{id}/deactivate")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<UserResponse>> deactivate(@PathVariable String id) {
    UserResponse response = userService.deactivate(id);
    return ResponseEntity.ok(responseFactory.success("User deactivated successfully", response));
  }

  @PutMapping("/{id}/reactivate")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<UserResponse>> reactivate(@PathVariable String id) {
    UserResponse response = userService.reactivate(id);
    return ResponseEntity.ok(responseFactory.success("User reactivated successfully", response));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
    userService.delete(id);
    return ResponseEntity.ok(responseFactory.deleted("User deleted successfully"));
  }
}
