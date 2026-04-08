package com.inmobiliaria.visit_calendar_service.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.inmobiliaria.visit_calendar_service.dto.ReassignmentRequestRequestDTO;
import com.inmobiliaria.visit_calendar_service.dto.ReassignmentRequestResponseDTO;
import com.inmobiliaria.visit_calendar_service.dto.RequestResponseDTO;
import com.inmobiliaria.visit_calendar_service.service.ReassignmentService;

import jakarta.validation.Valid;

/**
 * Controlador REST para la gestión de reasignaciones de citas.
 *
 * Endpoints implementados:
 * POST /api/visits/{id}/reassignment → Solicitar reasignación
 * PUT /api/reassignments/{id}/reply → Aceptar / Rechazar solicitud
 * GET /api/reassignments/received → Solicitudes pendientes del agente
 * GET /api/reassignments/pending/count → Cantidad (para badge de menú)
 *
 * Nota: El agente autenticado se extrae del header X-User-Id que el API Gateway
 * inyecta tras validar el JWT, siguiendo el patrón del proyecto.
 */
@RestController
@RequestMapping("/api")
public class ReassignmentController {

    private final ReassignmentService reassignmentService;

    public ReassignmentController(ReassignmentService reassignmentService) {
        this.reassignmentService = reassignmentService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/visits/{id}/reassignment
    // Solicitar reasignación de una cita propia
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Crea una solicitud de reasignación para la cita indicada.
     *
     * @param visitId           ID de la cita (path variable)
     * @param requestingAgentId ID del agente autenticado (inyectado por gateway)
     * @param dto               destinationAgentId + motivo
     */
    @PostMapping("/visits/{id}/reassignment")
    public ResponseEntity<?> requestReassignment(
            @PathVariable("id") String visitId,
            @RequestHeader("X-User-Id") String requestingAgentId,
            @Valid @RequestBody ReassignmentRequestRequestDTO dto) {
        try {
            ReassignmentRequestResponseDTO response = reassignmentService.requestReassignment(visitId,
                    requestingAgentId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/reassignments/{id}/reply
    // Aceptar o rechazar una solicitud de reasignación recibida
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * El agente destino acepta o rechaza una solicitud de reasignación.
     *
     * @param requestId          ID de la solicitud (path variable)
     * @param destinationAgentId ID del agente autenticado (inyectado por gateway)
     * @param dto                decision (ACEPTADA|RECHAZADA) + comentario opcional
     */
    @PutMapping("/reassignments/{id}/reply")
    public ResponseEntity<?> replyRequest(
            @PathVariable("id") String requestId,
            @RequestHeader("X-User-Id") String destinationAgentId,
            @Valid @RequestBody RequestResponseDTO dto) {
        try {
            ReassignmentRequestResponseDTO response = reassignmentService.replyRequest(requestId, destinationAgentId,
                    dto);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/reassignments/received
    // Bandeja de solicitudes pendientes del agente autenticado
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retorna todas las solicitudes de reasignación PENDIENTES dirigidas
     * al agente autenticado.
     *
     * @param destinationAgentId ID del agente autenticado (inyectado por gateway)
     */
    @GetMapping("/reassignments/received")
    public ResponseEntity<List<ReassignmentRequestResponseDTO>> getReceivedRequests(
            @RequestHeader("X-User-Id") String destinationAgentId) {
        List<ReassignmentRequestResponseDTO> requests = reassignmentService.getReceivedRequests(destinationAgentId);
        return ResponseEntity.ok(requests);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/reassignments/pending/count
    // Contador para badge de menú
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retorna el número de solicitudes pendientes para mostrar en el badge
     * de notificación del menú lateral.
     *
     * @param destinationAgentId ID del agente autenticado
     */
    @GetMapping("/reassignments/pending/count")
    public ResponseEntity<Map<String, Long>> countPendingRequests(
            @RequestHeader("X-User-Id") String destinationAgentId) {
        long count = reassignmentService.countPendingRequests(destinationAgentId);
        return ResponseEntity.ok(Map.of("pendientes", count));
    }
}