package com.inmobiliaria.visit_calendar_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.time.LocalDateTime;

/**
 * Documento MongoDB para los eventos del calendario compartido.
 * Cubre HU1 (visualizar calendario) y HU2 (programar visita).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "calendar_events")
@CompoundIndexes({
    // Índice para detectar conflictos: misma propiedad + solapamiento de horarios
    @CompoundIndex(name = "property_time_idx", def = "{'propertyId': 1, 'startTime': 1, 'endTime': 1}"),
    // Índice para consultar por agente
    @CompoundIndex(name = "agent_time_idx", def = "{'agentId': 1, 'startTime': 1}")
})
public class CalendarEvent {

    @Id
    private String id;

    /** ID del inmueble involucrado en el evento */
    private String propertyId;

    /** Nombre descriptivo del inmueble (para mostrar en el calendario) */
    private String propertyName;

    /** Dirección del inmueble */
    private String propertyAddress;

    /** ID del agente que creó el evento */
    private String agentId;

    /** Nombre completo del agente */
    private String agentName;

    /** Fecha y hora de inicio del evento */
    private LocalDateTime startTime;

    /** Fecha y hora de fin del evento */
    private LocalDateTime endTime;

    /**
     * Tipo de evento.
     * Valores posibles: VISIT (visita de agente), CLIENT_REQUEST (solicitud de cliente)
     */
    private EventType type;

    /**
     * Estado del evento.
     * Valores posibles: SCHEDULED, CONFIRMED, CANCELLED, COMPLETED
     */
    private EventStatus status;

    /** Notas adicionales del evento */
    private String notes;

    /** Fecha de creación del registro */
    private LocalDateTime createdAt;

    /** ID del cliente que solicitó la visita (opcional, solo para CLIENT_REQUEST) */
    private String clientId;

    /** Nombre del cliente que solicitó la visita */
    private String clientName;

    public enum EventType {
        VISIT,          // Visita programada por un agente
        CLIENT_REQUEST  // Solicitud de visita iniciada por un cliente
    }

    public enum EventStatus {
        SCHEDULED,   // Programada, pendiente de confirmación
        CONFIRMED,   // Confirmada
        CANCELLED,   // Cancelada
        COMPLETED    // Completada
    }
}
