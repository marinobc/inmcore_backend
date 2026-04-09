package com.inmobiliaria.property_service.security;

import java.time.Instant;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.inmobiliaria.property_service.client.IdentityClient;
import com.inmobiliaria.property_service.domain.AuditLog;
import com.inmobiliaria.property_service.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
    private final IdentityClient identityClient;

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void auditAction(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String adminId = (String) auth.getPrincipal();
            
            // Capture method arguments (the first arg is usually the ID)
            Object[] args = joinPoint.getArgs();
            String resourceId = (args.length > 0) ? args[0].toString() : "UNKNOWN";

            // Fetch admin's name from Identity Service
            String adminName = "Unknown Admin";
            try {
                var user = identityClient.findById(adminId);
                adminName = user.fullName();
            } catch (Exception e) {
                log.warn("Could not fetch admin name for audit: {}", e.getMessage());
            }

            AuditLog logEntry = AuditLog.builder()
                    .action(auditable.action())
                    .resourceId(resourceId)
                    .adminId(adminId)
                    .adminName(adminName)
                    .timestamp(Instant.now())
                    .details("Admin performed " + auditable.action() + " on resource " + resourceId)
                    .build();

            auditLogRepository.save(logEntry);
            log.info("Audit record saved for action: {}", auditable.action());

        } catch (Exception e) {
            log.error("Failed to generate audit log: {}", e.getMessage());
        }
    }
}