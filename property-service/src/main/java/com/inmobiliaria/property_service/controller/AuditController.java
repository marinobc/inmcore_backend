package com.inmobiliaria.property_service.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.inmobiliaria.property_service.domain.AuditLog;
import com.inmobiliaria.property_service.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/properties/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping("/{resourceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLog> getAuditLogs(@PathVariable String resourceId) {
        return auditLogRepository.findByResourceIdOrderByTimestampDesc(resourceId);
    }
}