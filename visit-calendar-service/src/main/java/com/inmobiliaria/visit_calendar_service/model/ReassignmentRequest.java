package com.inmobiliaria.visit_calendar_service.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Entidad que representa una solicitud de reasignación de cita.
 * HU: Reasignación de cita a colega (Agente Inmobiliario)
 */
@Document(collection = "reassignment_requests")
public class ReassignmentRequest {

    @Id
    private String id;

    /** ID de la cita (Visita) que se desea reasignar */
    private String visitId;

    /** ID del agente que solicita la reasignación (agente original de la cita) */
    private String requestingAgentId;

    /** ID del agente destino al que se solicita la reasignación */
    private String destinationAgentId;

    /** Motivo por el que se solicita la reasignación */
    private String reason;

    /**
     * Estado de la solicitud: PENDIENTE, ACEPTADA, RECHAZADA
     */
    private RequestStatus status;

    /** Fecha en que se creó la solicitud */
    private LocalDateTime requestedAt;

    /** Fecha en que el agente destino respondió la solicitud */
    private LocalDateTime repliedAt;

    /** Comentario opcional del agente destino al responder */
    private String commentReply;

    public enum RequestStatus {
        PENDING,
        ACCEPTED,
        REJECTED
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    public ReassignmentRequest() {
    }

    public ReassignmentRequest(String visitId,
            String requestingAgentId,
            String destinationAgentId,
            String reason) {
        this.visitId = visitId;
        this.requestingAgentId = requestingAgentId;
        this.destinationAgentId = destinationAgentId;
        this.reason = reason;
        this.status = RequestStatus.PENDING;
        this.requestedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVisitId() {
        return visitId;
    }

    public void setVisitId(String visitId) {
        this.visitId = visitId;
    }

    public String getRequestingAgentId() {
        return requestingAgentId;
    }

    public void setRequestingAgentId(String requestingAgentId) {
        this.requestingAgentId = requestingAgentId;
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

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getRepliedAt() {
        return repliedAt;
    }

    public void setRepliedAt(LocalDateTime repliedAt) {
        this.repliedAt = repliedAt;
    }

    public String getCommentReply() {
        return commentReply;
    }

    public void setCommentReply(String commentReply) {
        this.commentReply = commentReply;
    }
}