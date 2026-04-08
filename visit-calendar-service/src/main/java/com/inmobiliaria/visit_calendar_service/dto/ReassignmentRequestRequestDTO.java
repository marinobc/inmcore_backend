package com.inmobiliaria.visit_calendar_service.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO para crear una solicitud de reasignación.
 * Usado en: POST /api/visits/{id}/reassignment
 */
public class ReassignmentRequestRequestDTO {

    @NotBlank(message = "El ID del agente destino es obligatorio.")
    private String destinationAgentId;

    @NotBlank(message = "El motivo de la reasignación es obligatorio.")
    private String reason;

    public ReassignmentRequestRequestDTO() {
    }

    public String getDestinationAgentId() {
        return destinationAgentId;
    }

    public void setDestinationAgentId(String destinationAgentId) {
        this.destinationAgentId = destinationAgentId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}