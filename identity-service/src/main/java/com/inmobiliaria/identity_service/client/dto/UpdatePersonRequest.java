package com.inmobiliaria.identity_service.client.dto;

import java.time.LocalDate;
import java.util.List;

public record UpdatePersonRequest(
    String firstName,
    String lastName,
    LocalDate birthDate,
    String phone,
    String department,
    String position,
    LocalDate hireDate,
    String taxId,
    String address,
    List<String> propertyIds,
    String preferredContactMethod,
    String budget,
    String preferredZone,
    String preferredPropertyType,
    Integer preferredRooms,
    String assignedAgentId) {}
