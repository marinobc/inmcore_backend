package com.inmobiliaria.property_service.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "audit_logs")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class AuditLog {
    @Id
    private String id;
    private String action;       // e.g., "PROPERTY_DELETION"
    private String resourceId;   // ID of the deleted property
    private String adminId;      // ID from X-Auth-User-Id
    private String adminName;    // Full name retrieved from Identity Service
    private Instant timestamp;
    private String details;
}