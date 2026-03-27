package com.inmobiliaria.property_service.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AssignAgentRequest(@NotBlank String agentId) {}