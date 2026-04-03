package com.inmobiliaria.property_service.dto.response;

import com.inmobiliaria.property_service.domain.AssignmentHistory;
import com.inmobiliaria.property_service.domain.OperationType;
import com.inmobiliaria.property_service.domain.PriceHistory;
import java.util.List;
import java.util.Set;

public record PropertyResponse(
    String id,
    String title,
    String address,
    Double price,
    String type,
    OperationType operationType, // <-- Agregar
    Double m2,
    Integer rooms,
    String status,
    String assignedAgentId,
    List<String> imageUrls,
    List<AssignmentHistory> assignmentHistory,
    List<PriceHistory> priceHistory,
    Set<String> accessPolicy
) {}