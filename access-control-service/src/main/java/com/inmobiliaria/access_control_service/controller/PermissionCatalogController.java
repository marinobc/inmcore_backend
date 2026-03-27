package com.inmobiliaria.access_control_service.controller;

import com.inmobiliaria.access_control_service.dto.response.PermissionCatalogResponse;
import com.inmobiliaria.access_control_service.service.PermissionCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionCatalogController {

    private final PermissionCatalogService permissionCatalogService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<PermissionCatalogResponse> findAll() {
        return permissionCatalogService.findAll();
    }
}