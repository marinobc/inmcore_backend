package com.inmobiliaria.visit_calendar_service.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Entidad principal que representa una Visita/Cita inmobiliaria.
 *
 * CAMBIOS PARA HU REASIGNACIÓN:
 * - Se agregó el campo [reassignmentHistory] para mantener el registro
 * completo de todos los cambios de agente que tuvo la cita.
 *
 * Los demás campos ya existían en el proyecto; se listan aquí para
 * mostrar el documento completo y dónde encajan los nuevos campos.
 */
@Document(collection = "visits")
public class Visit {

    @Id
    private String id;

    /** ID del inmueble al que corresponde la visita */
    private String propertyId;

    /** ID del cliente que solicitó la visita */
    private String clientId;

    /**
     * ID del agente actualmente asignado a la visita.
     * Este campo se actualiza cuando se acepta una reasignación.
     */
    private String agentId;

    /** Fecha y hora programada para la visita */
    private LocalDateTime dateTime;

    /** Estado de la visita: PENDIENTE, CONFIRMADA, CANCELADA, COMPLETADA */
    private String status;

    /** Notas adicionales sobre la visita */
    private String notes;

    /** Fecha en que se creó la solicitud de visita */
    private LocalDateTime createdAt;

    /**
     * Historial de todas las reasignaciones que ha tenido esta cita.
     * Cada entrada registra: agente anterior, agente nuevo, motivo y fecha.
     * Se inicializa como lista vacía para evitar NPE.
     */
    private List<ReassignmentHistory> reassignmentHistory = new ArrayList<>();

    // ── Constructors ──────────────────────────────────────────────────────────

    public Visit() {
        this.createdAt = LocalDateTime.now();
        this.reassignmentHistory = new ArrayList<>();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(String propertyId) {
        this.propertyId = propertyId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<ReassignmentHistory> getReassignmentHistory() {
        return reassignmentHistory;
    }

    public void setReassignmentHistory(List<ReassignmentHistory> reassignmentHistory) {
        this.reassignmentHistory = reassignmentHistory;
    }
}