package com.inmobiliaria.visit_calendar_service.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.inmobiliaria.visit_calendar_service.model.ReassignmentRequest;
import com.inmobiliaria.visit_calendar_service.model.Visit;

import lombok.extern.slf4j.Slf4j;

/**
 * Notification service for appointment reassignment events.
 *
 * Makes real HTTP calls to the team's existing notification-service (port
 * 8083),
 * following the same pattern used by NotificationService for visit requests.
 *
 * Two notifications are sent during the reassignment flow:
 * 1. notifyReassignmentRequest → target agent receives the incoming request.
 * 2. notifyReassignmentDecision → requesting agent receives the accept/reject
 * decision.
 *
 * If the notification-service is unreachable, the error is logged but the main
 * business flow is never interrupted (fail-silent pattern).
 */
@Slf4j
@Service
public class ReassignmentNotificationService {

    private final RestTemplate restTemplate;

    @Value("${notification.service.url:http://localhost:8083}")
    private String notificationServiceUrl;

    public ReassignmentNotificationService() {
        this.restTemplate = new RestTemplate();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOTIFY TARGET AGENT — incoming reassignment request
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Notifies the target agent that they have received a reassignment request.
     * Called right after the requesting agent submits the solicitation.
     *
     * @param request The newly created reassignment request
     * @param visit   The appointment being reassigned
     * @return true if the notification-service responded with 2xx, false otherwise
     */
    public boolean notifyReassignmentRequest(ReassignmentRequest request, Visit visit) {
        try {
            String endpoint = notificationServiceUrl + "/notifications/reassignment-request";

            Map<String, Object> payload = Map.of(
                    "type", "REASSIGNMENT_REQUEST",
                    "recipientId", request.getDestinationAgentId(),
                    "subject", "New reassignment request — Appointment ID: " + request.getVisitId(),
                    "message", buildRequestMessage(request, visit),
                    "metadata", Map.of(
                            "reassignmentRequestId", request.getId(),
                            "visitId", request.getVisitId(),
                            "requestingAgentId", request.getRequestingAgentId(),
                            "destinationAgentId", request.getDestinationAgentId(),
                            "reason", request.getReason()));

            ResponseEntity<String> response = postToNotificationService(endpoint, payload);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[REASSIGNMENT] Request notification sent to agent '{}' for visit '{}'.",
                        request.getDestinationAgentId(), request.getVisitId());
                return true;
            }

            log.warn("[REASSIGNMENT] Notification-service returned non-2xx status: {}",
                    response.getStatusCode());

        } catch (Exception e) {
            // Never fail the main business flow if the notification call fails
            log.warn("[REASSIGNMENT] Could not reach notification-service. " +
                    "The request was saved successfully. Error: {}", e.getMessage());
        }

        // Fallback: structured console log for development environments
        log.info("[REASSIGNMENT - INTERNAL] Agent '{}' received a reassignment request " +
                "from agent '{}' for visit '{}'. Reason: {}",
                request.getDestinationAgentId(),
                request.getRequestingAgentId(),
                request.getVisitId(),
                request.getReason());

        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOTIFY REQUESTING AGENT — accept / reject decision
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Notifies the requesting agent of the decision made by the target agent.
     * Called right after the target agent accepts or rejects the request.
     *
     * @param request The reassignment request with its decision already applied
     * @return true if the notification-service responded with 2xx, false otherwise
     */
    public boolean notifyReassignmentDecision(ReassignmentRequest request) {
        try {
            String endpoint = notificationServiceUrl + "/notifications/reassignment-decision";

            Map<String, Object> payload = Map.of(
                    "type", "REASSIGNMENT_DECISION",
                    "recipientId", request.getRequestingAgentId(),
                    "subject", "Your reassignment request was " + request.getStatus().name()
                            + " — Appointment ID: " + request.getVisitId(),
                    "message", buildDecisionMessage(request),
                    "metadata", Map.of(
                            "reassignmentRequestId", request.getId(),
                            "visitId", request.getVisitId(),
                            "destinationAgentId", request.getDestinationAgentId(),
                            "decision", request.getStatus().name(),
                            "responseComment", request.getCommentReply() != null
                                    ? request.getCommentReply()
                                    : ""));

            ResponseEntity<String> response = postToNotificationService(endpoint, payload);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[REASSIGNMENT] Decision notification ('{}') sent to agent '{}'.",
                        request.getStatus().name(), request.getRequestingAgentId());
                return true;
            }

            log.warn("[REASSIGNMENT] Notification-service returned non-2xx status: {}",
                    response.getStatusCode());

        } catch (Exception e) {
            log.warn("[REASSIGNMENT] Could not reach notification-service. " +
                    "The decision was saved successfully. Error: {}", e.getMessage());
        }

        // Fallback: structured console log for development environments
        log.info("[REASSIGNMENT - INTERNAL] Agent '{}' — your reassignment request for visit '{}' " +
                "was {}. Comment: {}",
                request.getRequestingAgentId(),
                request.getVisitId(),
                request.getStatus().name(),
                request.getCommentReply() != null ? request.getCommentReply() : "(none)");

        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shared HTTP call to the notification-service endpoint.
     * Extracted to avoid duplication between the two public methods.
     */
    private ResponseEntity<String> postToNotificationService(String endpoint,
            Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        return restTemplate.postForEntity(endpoint, entity, String.class);
    }

    /**
     * Builds the human-readable message body for a new reassignment request,
     * sent to the target agent.
     */
    private String buildRequestMessage(ReassignmentRequest request, Visit visit) {
        return String.format(
                "Agent '%s' has requested that you take over the following appointment:\n\n" +
                        "  • Appointment ID : %s\n" +
                        "  • Scheduled date : %s\n" +
                        "  • Reason         : %s\n\n" +
                        "Please log in to the system to accept or reject this request.",
                request.getRequestingAgentId(),
                request.getVisitId(),
                visit.getDateTime() != null ? visit.getDateTime().toString() : "N/A",
                request.getReason());
    }

    /**
     * Builds the human-readable message body for an accept/reject decision,
     * sent back to the requesting agent.
     */
    private String buildDecisionMessage(ReassignmentRequest request) {
        String decisionLabel = "ACCEPTED".equals(request.getStatus().name())
                ? "accepted ✓"
                : "rejected ✗";

        String commentSection = (request.getCommentReply() != null
                && !request.getCommentReply().isBlank())
                        ? "\n  • Comment : " + request.getCommentReply()
                        : "";

        String outcome = "ACCEPTED".equals(request.getStatus().name())
                ? "The appointment has been transferred to the other agent's schedule."
                : "The appointment remains assigned to you — no changes were made.";

        return String.format(
                "Your reassignment request for appointment '%s' was %s by agent '%s'.%s\n\n%s",
                request.getVisitId(),
                decisionLabel,
                request.getDestinationAgentId(),
                commentSection,
                outcome);
    }
}