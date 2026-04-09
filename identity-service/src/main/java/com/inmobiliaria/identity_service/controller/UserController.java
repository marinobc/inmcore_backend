package com.inmobiliaria.identity_service.controller;

import com.inmobiliaria.identity_service.dto.request.AssignRoleRequest;
import com.inmobiliaria.identity_service.dto.request.CreateUserRequest;
import com.inmobiliaria.identity_service.dto.request.UpdateUserRequest;
import com.inmobiliaria.identity_service.dto.response.UserResponse;
import com.inmobiliaria.identity_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') or hasRole('AGENT')")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @GetMapping
    // MODIFICADO: Ahora el AGENT también puede listar para llenar el dropdown de filtros
    @PreAuthorize("hasRole('ADMIN') or hasRole('AGENT')")
    public List<UserResponse> findAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AGENT')")
    public UserResponse findById(@PathVariable String id) {
        return userService.findById(id);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse assignRole(@PathVariable String id, @Valid @RequestBody AssignRoleRequest request) {
        return userService.assignRole(id, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(@PathVariable String id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse deactivate(@PathVariable String id) {
        return userService.deactivate(id);
    }

    @PutMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse reactivate(@PathVariable String id) {
        return userService.reactivate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable String id) {
        userService.delete(id);
    }
}