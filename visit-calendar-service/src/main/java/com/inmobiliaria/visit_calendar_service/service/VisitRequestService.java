package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.dto.VisitCalendarDTOs.*;
import com.inmobiliaria.visit_calendar_service.exception.ResourceNotFoundException;
import com.inmobiliaria.visit_calendar_service.model.CalendarEvent;
import com.inmobiliaria.visit_calendar_service.model.VisitRequest;
import com.inmobiliaria.visit_calendar_service.repository.CalendarEventRepository;
import com.inmobiliaria.visit_calendar_service.repository.VisitRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de lógica de negocio para solicitudes de visita de clientes.
 *
 * HU3: Cliente buscador solicita agendar una cita.
 *   - PA1: El cliente solo ve propiedades disponibles (lógica en property-service, aquí solo el POST).
 *   - PA2: Al crear la solicitud, se genera una notificación al agente responsable.
 *   - PA3: Filtros de búsqueda (manejados en property-service, aquí la cita).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitRequestService {

    private final VisitRequestRepository visitRequestRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final NotificationService notificationService;

    /**
     * PA1 + PA2 de HU3:
     * El cliente solicita una cita para un inmueble.
     * Se persiste la solicitud y se envía notificación al agente.
     */
    public VisitRequestResponse createVisitRequest(ClientVisitRequestDTO dto) {
        log.debug("Nueva solicitud de visita: cliente={}, propiedad={}", dto.getClientId(), dto.getPropertyId());

        VisitRequest request = VisitRequest.builder()
                .propertyId(dto.getPropertyId())
                .propertyName(dto.getPropertyName())
                .agentId(dto.getAgentId())
                .agentName(dto.getAgentName())
                .clientId(dto.getClientId())
                .clientName(dto.getClientName())
                .clientEmail(dto.getClientEmail())
                .clientPhone(dto.getClientPhone())
                .preferredDateTime(dto.getPreferredDateTime())
                .alternativeDateTime(dto.getAlternativeDateTime())
                .message(dto.getMessage())
                .status(VisitRequest.RequestStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .notificationSent(false)
                .build();

        VisitRequest saved = visitRequestRepository.save(request);

        // PA2: Notificar al agente responsable del inmueble
        boolean notified = notificationService.notifyAgentOfVisitRequest(saved);
        if (notified) {
            saved.setNotificationSent(true);
            saved = visitRequestRepository.save(saved);
            log.info("Notificación enviada al agente {}: solicitud de visita id={}",
                    saved.getAgentId(), saved.getId());
        } else {
            log.warn("No se pudo enviar la notificación al agente {} para la solicitud {}",
                    saved.getAgentId(), saved.getId());
        }

        return toResponse(saved);
    }

    /**
     * El agente acepta la solicitud de visita y crea el evento en el calendario.
     */
    public VisitRequestResponse acceptVisitRequest(String requestId, String agentId) {
        VisitRequest request = visitRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada: " + requestId));

        if (!request.getAgentId().equals(agentId)) {
            throw new IllegalArgumentException("Solo el agente responsable puede aceptar esta solicitud");
        }

        // Crear evento en el calendario
        CalendarEvent event = CalendarEvent.builder()
                .propertyId(request.getPropertyId())
                .propertyName(request.getPropertyName())
                .agentId(request.getAgentId())
                .agentName(request.getAgentName())
                .clientId(request.getClientId())
                .clientName(request.getClientName())
                .startTime(request.getPreferredDateTime())
                .endTime(request.getPreferredDateTime().plusHours(1))
                .type(CalendarEvent.EventType.CLIENT_REQUEST)
                .status(CalendarEvent.EventStatus.CONFIRMED)
                .notes("Visita solicitada por el cliente: " + request.getClientName())
                .createdAt(LocalDateTime.now())
                .build();

        CalendarEvent savedEvent = calendarEventRepository.save(event);

        // Actualizar solicitud
        request.setStatus(VisitRequest.RequestStatus.ACCEPTED);
        request.setCalendarEventId(savedEvent.getId());
        request.setUpdatedAt(LocalDateTime.now());
        VisitRequest updated = visitRequestRepository.save(request);

        log.info("Solicitud aceptada: requestId={}, calendarEventId={}", requestId, savedEvent.getId());
        return toResponse(updated);
    }

    /**
     * El agente rechaza la solicitud de visita.
     */
    public VisitRequestResponse rejectVisitRequest(String requestId, String agentId) {
        VisitRequest request = visitRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada: " + requestId));

        if (!request.getAgentId().equals(agentId)) {
            throw new IllegalArgumentException("Solo el agente responsable puede rechazar esta solicitud");
        }

        request.setStatus(VisitRequest.RequestStatus.REJECTED);
        request.setUpdatedAt(LocalDateTime.now());
        VisitRequest updated = visitRequestRepository.save(request);

        log.info("Solicitud rechazada: requestId={}", requestId);
        return toResponse(updated);
    }

    /**
     * Obtiene todas las solicitudes pendientes de un agente.
     */
    public List<VisitRequestResponse> getPendingRequestsForAgent(String agentId) {
        return visitRequestRepository
                .findByAgentIdAndStatus(agentId, VisitRequest.RequestStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todas las solicitudes de un cliente.
     */
    public List<VisitRequestResponse> getClientRequests(String clientId) {
        return visitRequestRepository.findByClientId(clientId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public int getVisitCountForProperty(String propertyId) {
        return calendarEventRepository.findByPropertyId(propertyId).size();
        //return visitRequestRepository.findByPropertyId(propertyId).size();
    }

    // =====================================================================
    //  Helper
    // =====================================================================

    private VisitRequestResponse toResponse(VisitRequest r) {
        return VisitRequestResponse.builder()
                .id(r.getId())
                .propertyId(r.getPropertyId())
                .propertyName(r.getPropertyName())
                .agentId(r.getAgentId())
                .agentName(r.getAgentName())
                .clientId(r.getClientId())
                .clientName(r.getClientName())
                .clientEmail(r.getClientEmail())
                .clientPhone(r.getClientPhone())
                .preferredDateTime(r.getPreferredDateTime())
                .alternativeDateTime(r.getAlternativeDateTime())
                .message(r.getMessage())
                .status(r.getStatus())
                .calendarEventId(r.getCalendarEventId())
                .createdAt(r.getCreatedAt())
                .notificationSent(r.isNotificationSent())
                .build();
    }
}
