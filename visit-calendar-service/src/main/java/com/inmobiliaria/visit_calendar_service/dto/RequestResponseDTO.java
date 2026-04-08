package com.inmobiliaria.visit_calendar_service.dto;

import com.inmobiliaria.visit_calendar_service.model.ReassignmentRequest;

import jakarta.validation.constraints.NotNull;

/**
 * DTO para responder (aceptar/rechazar) una solicitud de reasignación.
 * Usado en: PUT /api/reassignments/{id}/reply
 */
public class RequestResponseDTO {

    /**
     * Decisión del agente destino: ACEPTADA o RECHAZADA.
     */
    @NotNull(message = "La decisión (ACEPTADA/RECHAZADA) es obligatoria.")
    private ReassignmentRequest.RequestStatus decision;

    /** Comentario opcional del agente destino */
    private String comment;

    public RequestResponseDTO() {
    }

    public ReassignmentRequest.RequestStatus getDecision() {
        return decision;
    }

    public void setDecision(ReassignmentRequest.RequestStatus decision) {
        this.decision = decision;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}