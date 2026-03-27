package com.inmobiliaria.property_service.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssignmentHistory {
    private String agentId;
    private Instant assignedAt;
    private String assignedBy;
}