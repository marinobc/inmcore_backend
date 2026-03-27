package com.inmobiliaria.visit_calendar_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * Documento MongoDB para las solicitudes de visita de clientes buscadores.
 * Cubre HU3: el cliente solicita cita → se notifica al agente responsable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "visit_requests")
public class VisitRequest {

    @Id
    private String id;

    /** ID del inmueble que el cliente quiere visitar */
    @Indexed
    private String propertyId;

    /** Nombre descriptivo del inmueble */
    private String propertyName;

    /** ID del agente responsable del inmueble */
    @Indexed
    private String agentId;

    /** Nombre del agente responsable */
    private String agentName;

    /** ID del cliente que solicita la visita */
    @Indexed
    private String clientId;

    /** Nombre completo del cliente */
    private String clientName;

    /** Email del cliente para contacto */
    private String clientEmail;

    /** Teléfono del cliente (opcional) */
    private String clientPhone;

    /** Horario preferido propuesto por el cliente */
    private LocalDateTime preferredDateTime;

    /** Horario alternativo propuesto por el cliente (opcional) */
    private LocalDateTime alternativeDateTime;

    /** Mensaje o comentario adicional del cliente */
    private String message;

    /**
     * Estado de la solicitud.
     * PENDING → agente aún no ha respondido
     * ACCEPTED → agente aceptó y creó el evento en el calendario
     * REJECTED → agente rechazó la solicitud
     */
    private RequestStatus status;

    /** ID del evento creado en el calendario al aceptar la solicitud */
    private String calendarEventId;

    /** Fecha en que se creó la solicitud */
    private LocalDateTime createdAt;

    /** Fecha de la última actualización */
    private LocalDateTime updatedAt;

    /** Indica si la notificación al agente fue enviada */
    private boolean notificationSent;

    public enum RequestStatus {
        PENDING,
        ACCEPTED,
        REJECTED
    }
}
