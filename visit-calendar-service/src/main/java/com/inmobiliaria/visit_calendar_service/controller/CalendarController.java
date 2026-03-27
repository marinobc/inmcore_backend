package com.inmobiliaria.visit_calendar_service.controller;

import com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.*;
import com.inmobiliaria.visit_calendar_service.service.CalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Controlador REST para el calendario compartido y la programación de visitas.
 *
 * HU1: GET  /calendar              → Visualizar calendario del equipo
 * HU2: POST /visits                → Programar una visita
 *      GET  /visits/conflict-check → Verificar conflicto antes de crear
 *      GET  /visits/agenda/{day}   → Agenda del día de un agente (PA3 HU2)
 *      PATCH /visits/{id}/cancel   → Cancelar una visita
 *      GET  /visits/{id}           → Detalle de un evento
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CalendarController {

    private final CalendarService calendarService;

    // -----------------------------------------------------------------------
    //  HU1: Visualizar calendario compartido del equipo
    // -----------------------------------------------------------------------

    /**
     * GET /api/calendar
     *
     * Devuelve el calendario del equipo en el rango de fechas indicado.
     * Parámetros opcionales de filtro: agentId, propertyId.
     * El header X-Agent-Id identifica al agente autenticado para marcar
     * sus propios eventos como ownEvent=true (diferenciación visual, PA1).
     *
     * PA1: ver visitas de todo el equipo, propias destacadas
     * PA3: filtrar por propiedad → solo eventos de ese inmueble
     *
     * Ejemplos:
     *   GET /api/calendar?from=2025-06-01T00:00:00&to=2025-06-07T23:59:59
     *   GET /api/calendar?propertyId=abc123&from=...&to=...
     *   GET /api/calendar?agentId=xyz&from=...&to=...
     */
    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<CalendarResponse>> getCalendar(
            @RequestHeader(value = "X-Agent-Id", required = false) String requestingAgentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String propertyId) {

        log.debug("GET /api/calendar: agente={}, desde={}, hasta={}", requestingAgentId, from, to);

        CalendarResponse response = calendarService.getCalendar(
                requestingAgentId, from, to, agentId, propertyId);

        return ResponseEntity.ok(ApiResponse.ok(
                "Calendario obtenido correctamente", response));
    }

    // -----------------------------------------------------------------------
    //  HU2: Programar visita
    // -----------------------------------------------------------------------

    /**
     * POST /api/visits
     *
     * Programa una nueva visita.
     * Valida que el inmueble no tenga conflicto de horario (PA2).
     * Al confirmarse, aparece en el calendario compartido (PA1).
     *
     * Body: CreateVisitRequest
     * Response 201: CalendarEventResponse (la visita creada)
     * Response 409: ConflictResponse (si hay conflicto de horario)
     */
    @PostMapping("/visits")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> createVisit(
            @Valid @RequestBody CreateVisitRequest request) {

        log.debug("POST /api/visits: propiedad={}, agente={}, inicio={}",
                request.getPropertyId(), request.getAgentId(), request.getStartTime());

        CalendarEventResponse created = calendarService.createVisit(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Visita programada exitosamente", created));
    }

    /**
     * GET /api/visits/conflict-check
     *
     * Verifica si existe conflicto de horario ANTES de enviar el formulario.
     * Permite al frontend alertar al usuario (PA2 HU1 + PA2 HU2) sin crear el evento.
     *
     * Params: propertyId, startTime, endTime
     */
    @GetMapping("/visits/conflict-check")
    public ResponseEntity<ApiResponse<ConflictResponse>> checkConflict(
            @RequestParam String propertyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        ConflictResponse result = calendarService.checkConflict(propertyId, startTime, endTime);

        return ResponseEntity.ok(ApiResponse.ok(
                result.isHasConflict() ? "Conflicto detectado" : "Horario disponible", result));
    }

    /**
     * GET /api/visits/agenda
     *
     * Agenda del día de un agente específico (PA3 HU2).
     * Param: agentId, day (ISO datetime del día a consultar)
     */
    @GetMapping("/visits/agenda")
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getDayAgenda(
            @RequestParam String agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime day) {

        List<CalendarEventResponse> agenda = calendarService.getAgentDayAgenda(agentId, day);

        return ResponseEntity.ok(ApiResponse.ok(
                "Agenda obtenida correctamente", agenda));
    }

    /**
     * GET /api/visits/{id}
     * Detalle de un evento de visita.
     */
    @GetMapping("/visits/{id}")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> getVisitById(
            @PathVariable String id,
            @RequestHeader(value = "X-Agent-Id", required = false) String agentId) {

        CalendarEventResponse event = calendarService.getById(id, agentId);
        return ResponseEntity.ok(ApiResponse.ok("Evento encontrado", event));
    }

    /**
     * PATCH /api/visits/{id}/cancel
     * Cancela una visita (solo el agente dueño).
     */
    @PatchMapping("/visits/{id}/cancel")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> cancelVisit(
            @PathVariable String id,
            @RequestHeader("X-Agent-Id") String agentId) {

        CalendarEventResponse cancelled = calendarService.cancelEvent(id, agentId);
        return ResponseEntity.ok(ApiResponse.ok("Visita cancelada", cancelled));
    }
}
