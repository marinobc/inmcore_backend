package com.inmobiliaria.visit_calendar_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.inmobiliaria.visit_calendar_service.model.ReassignmentRequest;
import com.inmobiliaria.visit_calendar_service.model.Visit;

/**
 * Servicio de notificaciones interno del visit-calendar-service.
 *
 * En producción, este servicio puede comunicarse con el notification-service
 * (puerto 8083) vía HTTP/REST o publicar eventos a una cola de mensajes.
 *
 * Por ahora implementa logs estructurados como placeholder fácilmente
 * reemplazable con llamadas reales al notification-service.
 */
@Service
public class ReassignmentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ReassignmentNotificationService.class);

    /**
     * Notifica al agente destino que recibió una solicitud de reasignación.
     *
     * @param request Solicitud creada
     * @param visit   Visita/cita afectada
     */
    public void notifyReassignmentRequest(ReassignmentRequest request, Visit visit) {
        log.info("[NOTIFICATION] Solicitud de reasignación recibida." +
                " Para agente: {} | Cita: {} | Motivo: {}",
                request.getDestinationAgentId(),
                request.getVisitId(),
                request.getReason());

        /*
         * TODO: Reemplazar con llamada real al notification-service:
         *
         * NotificationRequestDTO notification = new NotificationRequestDTO();
         * notification.setDestinatarioId(request.getDestinationAgentId());
         * notification.setTipo("SOLICITUD_REASIGNACION");
         * notification.
         * setMensaje("Tienes una nueva solicitud de reasignación para la cita del " +
         * visita.getFechaHora());
         * notification.setReferencia(request.getId());
         * notificationClient.enviar(notification);
         */
    }

    /**
     * Notifica al agente solicitante la decisión tomada por el agente destino.
     *
     * @param request Solicitud con decisión ya aplicada
     */
    public void notifyReassignmentDecision(ReassignmentRequest request) {
        String decision = request.getStatus().name();

        log.info("[NOTIFICATION] Decisión de reasignación: {}. Para agente solicitante: {} | Solicitud: {}",
                decision,
                request.getRequestingAgentId(),
                request.getId());

        /*
         * TODO: Reemplazar con llamada real al notification-service:
         *
         * NotificationRequestDTO notification = new NotificationRequestDTO();
         * notification.setDestinatarioId(request.getRequestingAgentId());
         * notification.setTipo("DECISION_REASIGNACION");
         * notification.setMensaje("Tu request de reasignación fue " + decision +
         * (request.getComentarioRespuesta() != null
         * ? ". Comentario: " + request.getComentarioRespuesta()
         * : ""));
         * notification.setReferencia(request.getId());
         * notificationClient.enviar(notification);
         */
    }
}