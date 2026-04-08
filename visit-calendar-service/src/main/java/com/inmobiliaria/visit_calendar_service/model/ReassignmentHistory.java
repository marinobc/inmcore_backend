package com.inmobiliaria.visit_calendar_service.model;

import java.time.LocalDateTime;

/**
 * Registro histórico de una reasignación aplicada a una Cita/Visita.
 * Se almacena embebido dentro del documento de la Visita para mantener
 * el historial completo de reasignaciones.
 */
public class ReassignmentHistory {

    /** ID de la solicitud de reasignación que originó este cambio */
    private String requestId;

    /** ID del agente que tenía asignada la cita antes del cambio */
    private String previousAgentId;

    /** ID del agente que recibió la cita tras la reasignación */
    private String newAgentId;

    /** Motivo indicado al solicitar la reasignación */
    private String reason;

    /** Fecha en que se aplicó la reasignación */
    private LocalDateTime reassignedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    public ReassignmentHistory() {
    }

    public ReassignmentHistory(String requestId,
            String previousAgentId,
            String newAgentId,
            String reason) {
        this.requestId = requestId;
        this.previousAgentId = previousAgentId;
        this.newAgentId = newAgentId;
        this.reason = reason;
        this.reassignedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getPreviousAgentId() {
        return previousAgentId;
    }

    public void setPreviousAgentId(String previousAgentId) {
        this.previousAgentId = previousAgentId;
    }

    public String getNewAgentId() {
        return newAgentId;
    }

    public void setNewAgentId(String newAgentId) {
        this.newAgentId = newAgentId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getReassignedAt() {
        return reassignedAt;
    }

    public void setReassignedAt(LocalDateTime reassignedAt) {
        this.reassignedAt = reassignedAt;
    }
}