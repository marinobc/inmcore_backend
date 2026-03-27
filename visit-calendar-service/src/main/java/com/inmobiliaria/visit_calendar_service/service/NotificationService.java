package com.inmobiliaria.visit_calendar_service.service;

import com.inmobiliaria.visit_calendar_service.model.VisitRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;

import java.util.Map;

/**
 * Servicio de notificaciones.
 * Llama al notification-service existente (puerto 8083) para avisar al agente
 * que un cliente ha solicitado una visita a su inmueble.
 *
 * HU3 - PA2: El agente responsable recibe la notificación de la solicitud.
 */
@Slf4j
@Service
public class NotificationService {

    private final RestTemplate restTemplate;

    @Value("${notification.service.url:http://localhost:8083}")
    private String notificationServiceUrl;

    public NotificationService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Notifica al agente responsable que un cliente solicitó una visita.
     * Integra con el notification-service existente del equipo.
     *
     * @param visitRequest La solicitud de visita recién creada
     * @return true si la notificación fue enviada correctamente
     */
    public boolean notifyAgentOfVisitRequest(VisitRequest visitRequest) {
        try {
            String endpoint = notificationServiceUrl + "/notifications/visit-request";

            Map<String, Object> payload = Map.of(
                "type",         "VISIT_REQUEST",
                "recipientId",  visitRequest.getAgentId(),
                "subject",      "Nueva solicitud de visita - " + visitRequest.getPropertyName(),
                "message",      buildNotificationMessage(visitRequest),
                "metadata", Map.of(
                    "visitRequestId",  visitRequest.getId(),
                    "propertyId",      visitRequest.getPropertyId(),
                    "propertyName",    visitRequest.getPropertyName(),
                    "clientName",      visitRequest.getClientName(),
                    "clientEmail",     visitRequest.getClientEmail(),
                    "preferredDate",   visitRequest.getPreferredDateTime().toString()
                )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Notificación enviada al agente {} para la propiedad {}",
                        visitRequest.getAgentId(), visitRequest.getPropertyName());
                return true;
            }

        } catch (Exception e) {
            // No fallar el flujo principal si la notificación falla
            log.warn("No se pudo conectar con el notification-service. " +
                     "La solicitud se guardó igualmente. Error: {}", e.getMessage());
        }

        // Fallback: log de la notificación en consola para entornos de desarrollo
        log.info("[NOTIFICACIÓN INTERNA] Agente '{}' — Nueva solicitud de visita de '{}' " +
                 "para el inmueble '{}' el {}",
                visitRequest.getAgentName(),
                visitRequest.getClientName(),
                visitRequest.getPropertyName(),
                visitRequest.getPreferredDateTime());

        return false;
    }

    private String buildNotificationMessage(VisitRequest r) {
        return String.format(
            "El cliente %s (%s) ha solicitado una visita al inmueble '%s'.\n" +
            "Horario preferido: %s\n" +
            "%s" +
            "Mensaje del cliente: %s\n\n" +
            "Por favor ingresa al sistema para aceptar o rechazar la solicitud.",
            r.getClientName(),
            r.getClientEmail(),
            r.getPropertyName(),
            r.getPreferredDateTime(),
            r.getAlternativeDateTime() != null
                ? "Horario alternativo: " + r.getAlternativeDateTime() + "\n"
                : "",
            r.getMessage() != null ? r.getMessage() : "(Sin mensaje)"
        );
    }
}
