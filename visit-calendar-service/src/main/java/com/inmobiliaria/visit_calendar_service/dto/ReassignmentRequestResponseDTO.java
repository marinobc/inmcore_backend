package com.inmobiliaria.visit_calendar_service.dto;

import java.time.LocalDateTime;

import com.inmobiliaria.visit_calendar_service.model.ReassignmentRequest;

/**
 * DTO de respuesta con los datos de una SolicitudReasignacion.
 */
public class ReassignmentRequestResponseDTO {

    private String id;
    private String visitId;
    private String requestingAgentId;
    private String destinationAgentId;
    private String reason;
    private ReassignmentRequest.RequestStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime repliedAt;
    private String commentReply;

    // ── Constructor desde entidad ─────────────────────────────────────────────

    public static ReassignmentRequestResponseDTO from(ReassignmentRequest s) {
        ReassignmentRequestResponseDTO dto = new ReassignmentRequestResponseDTO();
        dto.id = s.getId();
        dto.visitId = s.getVisitId();
        dto.requestingAgentId = s.getRequestingAgentId();
        dto.destinationAgentId = s.getDestinationAgentId();
        dto.reason = s.getReason();
        dto.status = s.getStatus();
        dto.requestedAt = s.getRequestedAt();
        dto.repliedAt = s.getRepliedAt();
        dto.commentReply = s.getCommentReply();
        return dto;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public String getVisitId() {
        return visitId;
    }

    public String getRequestingAgentId() {
        return requestingAgentId;
    }

    public String getDestinationAgentId() {
        return destinationAgentId;
    }

    public String getReason() {
        return reason;
    }

    public ReassignmentRequest.RequestStatus getStatus() {
        return status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getRepliedAt() {
        return repliedAt;
    }

    public String getCommentReply() {
        return commentReply;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setVisitId(String visitId) {
        this.visitId = visitId;
    }

    public void setRequestingAgentId(String requestingAgentId) {
        this.requestingAgentId = requestingAgentId;
    }

    public void setDestinationAgentId(String destinationAgentId) {
        this.destinationAgentId = destinationAgentId;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public void setStatus(ReassignmentRequest.RequestStatus status) {
        this.status = status;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public void setRepliedAt(LocalDateTime repliedAt) {
        this.repliedAt = repliedAt;
    }

    public void setCommentReply(String commentReply) {
        this.commentReply = commentReply;
    }
}