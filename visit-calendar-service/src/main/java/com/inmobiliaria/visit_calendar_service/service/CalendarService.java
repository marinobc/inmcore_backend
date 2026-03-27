package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.*;
import com.inmobiliaria.visit_calendar_service.exception.ResourceNotFoundException;
import com.inmobiliaria.visit_calendar_service.exception.ScheduleConflictException;
import com.inmobiliaria.visit_calendar_service.model.CalendarEvent;
import com.inmobiliaria.visit_calendar_service.repository.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de lógica de negocio para el calendario de visitas.
 *
 * HU1: Visualizar calendario compartido con filtros (agente, fecha, propiedad).
 * HU2: Programar visita validando disponibilidad y detectando conflictos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CalendarEventRepository calendarEventRepository;

    // =====================================================================
    //  HU1: GET /calendar — Visualizar calendario compartido del equipo
    // =====================================================================

    /**
     * PA1: Retorna todos los eventos del calendario en un rango de fechas.
     * Los eventos del agente autenticado se marcan con ownEvent=true (para resaltarlos visualmente).
     * PA3: Si se pasa propertyId, filtra solo por esa propiedad.
     *
     * @param requestingAgentId ID del agente autenticado (para marcar sus propios eventos)
     * @param from              Inicio del rango de fechas
     * @param to                Fin del rango de fechas
     * @param agentId           Filtro opcional por agente específico
     * @param propertyId        Filtro opcional por propiedad específica
     */
    public CalendarResponse getCalendar(
            String requestingAgentId,
            LocalDateTime from,
            LocalDateTime to,
            String agentId,
            String propertyId) {

        log.debug("Obteniendo calendario: agenteFiltro={}, propiedadFiltro={}, desde={}, hasta={}",
                agentId, propertyId, from, to);

        List<CalendarEvent> events;

        if (propertyId != null && !propertyId.isBlank()) {
            // PA3: Filtro por propiedad específica
            events = calendarEventRepository.findByPropertyIdAndDateRange(propertyId, from, to);
        } else if (agentId != null && !agentId.isBlank()) {
            // Filtro por agente específico
            events = calendarEventRepository.findByAgentIdAndDateRange(agentId, from, to);
        } else {
            // Vista completa del equipo
            events = calendarEventRepository.findByDateRange(from, to);
        }

        List<CalendarEventResponse> responses = events.stream()
                .map(event -> toResponse(event, requestingAgentId))
                .collect(Collectors.toList());

        long myEventsCount = responses.stream().filter(CalendarEventResponse::isOwnEvent).count();

        return CalendarResponse.builder()
                .events(responses)
                .from(from)
                .to(to)
                .totalEvents(responses.size())
                .myEvents((int) myEventsCount)
                .build();
    }

    // =====================================================================
    //  HU2: POST /visits — Programar visita con validación de conflictos
    // =====================================================================

    /**
     * PA2: Valida si existe conflicto de horario ANTES de crear el evento.
     * Retorna detalles del conflicto y una sugerencia de horario alternativo.
     */
    public ConflictResponse checkConflict(String propertyId, LocalDateTime startTime, LocalDateTime endTime) {
        validateDateRange(startTime, endTime);

        List<CalendarEvent> conflicts = calendarEventRepository
                .findConflictingEventsForNew(propertyId, startTime, endTime);

        if (conflicts.isEmpty()) {
            return ConflictResponse.builder()
                    .hasConflict(false)
                    .message("El horario está disponible")
                    .conflictingEvents(List.of())
                    .build();
        }

        // Sugerir horario después del último evento conflictivo
        LocalDateTime suggestedStart = conflicts.stream()
                .map(CalendarEvent::getEndTime)
                .max(LocalDateTime::compareTo)
                .orElse(endTime)
                .plusMinutes(30);

        long durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
        LocalDateTime suggestedEnd = suggestedStart.plusMinutes(durationMinutes);

        return ConflictResponse.builder()
                .hasConflict(true)
                .message("Ya existe una visita programada para este inmueble en ese horario. " +
                         "Por favor selecciona otro horario.")
                .conflictingEvents(conflicts.stream()
                        .map(e -> toResponse(e, null))
                        .collect(Collectors.toList()))
                .suggestedStartTime(suggestedStart)
                .suggestedEndTime(suggestedEnd)
                .build();
    }

    /**
     * PA1 + PA2 + PA3 de HU2:
     * Crea un nuevo evento de visita en el calendario.
     * Lanza ScheduleConflictException si ya existe un conflicto de horario.
     * La visita aparece automáticamente en el calendario compartido (PA1).
     */
    public CalendarEventResponse createVisit(CreateVisitRequest request) {
        validateDateRange(request.getStartTime(), request.getEndTime());

        // Verificar conflictos de horario (PA2)
        List<CalendarEvent> conflicts = calendarEventRepository
                .findConflictingEventsForNew(
                        request.getPropertyId(),
                        request.getStartTime(),
                        request.getEndTime());

        if (!conflicts.isEmpty()) {
            throw new ScheduleConflictException(
                    "Ya existe una visita programada para el inmueble '" +
                    request.getPropertyName() + "' en ese horario. " +
                    "Por favor selecciona otro horario.");
        }

        CalendarEvent event = CalendarEvent.builder()
                .propertyId(request.getPropertyId())
                .propertyName(request.getPropertyName())
                .propertyAddress(request.getPropertyAddress())
                .agentId(request.getAgentId())
                .agentName(request.getAgentName())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .type(CalendarEvent.EventType.VISIT)
                .status(CalendarEvent.EventStatus.SCHEDULED)
                .notes(request.getNotes())
                .createdAt(LocalDateTime.now())
                .build();

        CalendarEvent saved = calendarEventRepository.save(event);
        log.info("Visita creada exitosamente: id={}, propiedad={}, agente={}, inicio={}",
                saved.getId(), saved.getPropertyName(), saved.getAgentName(), saved.getStartTime());

        return toResponse(saved, request.getAgentId());
    }

    /**
     * PA3 de HU2: Obtiene la agenda del día para un agente específico.
     */
    public List<CalendarEventResponse> getAgentDayAgenda(String agentId, LocalDateTime day) {
        LocalDateTime dayStart = day.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1).minusNanos(1);
        List<CalendarEvent> events = calendarEventRepository.findByDayAndAgent(dayStart, dayEnd, agentId);
        return events.stream()
                .map(e -> toResponse(e, agentId))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene un evento por ID.
     */
    public CalendarEventResponse getById(String id, String requestingAgentId) {
        CalendarEvent event = calendarEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado: " + id));
        return toResponse(event, requestingAgentId);
    }

    /**
     * Cancela un evento (solo el agente dueño puede cancelar el suyo).
     */
    public CalendarEventResponse cancelEvent(String id, String agentId) {
        CalendarEvent event = calendarEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado: " + id));

        if (!event.getAgentId().equals(agentId)) {
            throw new IllegalArgumentException("Solo el agente responsable puede cancelar esta visita");
        }

        event.setStatus(CalendarEvent.EventStatus.CANCELLED);
        CalendarEvent saved = calendarEventRepository.save(event);
        log.info("Visita cancelada: id={}", id);
        return toResponse(saved, agentId);
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private void validateDateRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin son obligatorias");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin");
        }
        if (start.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("No se puede programar una visita en el pasado");
        }
    }

    private CalendarEventResponse toResponse(CalendarEvent event, String requestingAgentId) {
        return CalendarEventResponse.builder()
                .id(event.getId())
                .propertyId(event.getPropertyId())
                .propertyName(event.getPropertyName())
                .propertyAddress(event.getPropertyAddress())
                .agentId(event.getAgentId())
                .agentName(event.getAgentName())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .type(event.getType())
                .status(event.getStatus())
                .notes(event.getNotes())
                .createdAt(event.getCreatedAt())
                .clientId(event.getClientId())
                .clientName(event.getClientName())
                // PA1 de HU1: marca visualmente los eventos del agente autenticado
                .ownEvent(requestingAgentId != null && requestingAgentId.equals(event.getAgentId()))
                .build();
    }
}
