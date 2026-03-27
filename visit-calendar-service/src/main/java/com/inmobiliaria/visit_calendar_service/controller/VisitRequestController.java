package com.inmobiliaria.visit_calendar_service.controller;

import com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.*;
import com.inmobiliaria.visit_calendar_service.service.VisitRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para solicitudes de visita de clientes buscadores.
 *
 * HU3:
 *   POST  /api/visit-requests                    → Cliente solicita cita (PA1 + PA2)
 *   GET   /api/visit-requests/agent/{agentId}    → Agente ve sus solicitudes pendientes
 *   GET   /api/visit-requests/client/{clientId}  → Cliente ve sus propias solicitudes
 *   PATCH /api/visit-requests/{id}/accept        → Agente acepta → crea evento en calendario
 *   PATCH /api/visit-requests/{id}/reject        → Agente rechaza la solicitud
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/visit-requests")
public class VisitRequestController {

    private final VisitRequestService visitRequestService;

    /**
     * POST /api/visit-requests
     *
     * El cliente buscador solicita una cita para visitar un inmueble.
     * PA1: El inmueble debe estar "Disponible" (validado en property-service).
     * PA2: Se genera notificación al agente responsable del inmueble.
     *
     * Body: ClientVisitRequestDTO
     * Response 201: VisitRequestResponse
     */
    @PostMapping
    public ResponseEntity<ApiResponse<VisitRequestResponse>> createVisitRequest(
            @Valid @RequestBody ClientVisitRequestDTO dto) {

        log.debug("POST /api/visit-requests: cliente={}, propiedad={}",
                dto.getClientId(), dto.getPropertyId());

        VisitRequestResponse response = visitRequestService.createVisitRequest(dto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        "Solicitud de visita enviada correctamente. " +
                        "El agente responsable ha sido notificado.",
                        response));
    }

    /**
     * GET /api/visit-requests/agent/{agentId}
     *
     * El agente consulta sus solicitudes pendientes.
     * Útil para el panel del agente donde ve las solicitudes entrantes.
     */
    @GetMapping("/agent/{agentId}")
    public ResponseEntity<ApiResponse<List<VisitRequestResponse>>> getPendingForAgent(
            @PathVariable String agentId) {

        List<VisitRequestResponse> pending = visitRequestService.getPendingRequestsForAgent(agentId);

        return ResponseEntity.ok(ApiResponse.ok(
                "Solicitudes pendientes obtenidas", pending));
    }

    /**
     * GET /api/visit-requests/client/{clientId}
     *
     * El cliente consulta el estado de sus solicitudes enviadas.
     */
    @GetMapping("/client/{clientId}")
    public ResponseEntity<ApiResponse<List<VisitRequestResponse>>> getClientRequests(
            @PathVariable String clientId) {

        List<VisitRequestResponse> requests = visitRequestService.getClientRequests(clientId);

        return ResponseEntity.ok(ApiResponse.ok(
                "Solicitudes del cliente obtenidas", requests));
    }
    
    @GetMapping("/count/property/{propertyId}")
        public ResponseEntity<ApiResponse<Integer>> getVisitCountForProperty(
                @PathVariable String propertyId) {
        
        // Security: Verify the authenticated user owns this property
        // We'll need to call property-service to check ownership
        
        int count = visitRequestService.getVisitCountForProperty(propertyId);
        return ResponseEntity.ok(ApiResponse.ok("Visit count retrieved", count));
    }

    /**
     * PATCH /api/visit-requests/{id}/accept
     *
     * El agente acepta la solicitud.
     * Crea automáticamente el evento en el calendario compartido.
     */
    @PatchMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<VisitRequestResponse>> acceptRequest(
            @PathVariable String id,
            @RequestHeader("X-Agent-Id") String agentId) {

        VisitRequestResponse response = visitRequestService.acceptVisitRequest(id, agentId);

        return ResponseEntity.ok(ApiResponse.ok(
                "Solicitud aceptada. La visita fue añadida al calendario.", response));
    }

    /**
     * PATCH /api/visit-requests/{id}/reject
     *
     * El agente rechaza la solicitud.
     */
    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<VisitRequestResponse>> rejectRequest(
            @PathVariable String id,
            @RequestHeader("X-Agent-Id") String agentId) {

        VisitRequestResponse response = visitRequestService.rejectVisitRequest(id, agentId);

        return ResponseEntity.ok(ApiResponse.ok(
                "Solicitud rechazada.", response));
    }
}
