package com.inmobiliaria.visit_calendar_service.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.inmobiliaria.visit_calendar_service.model.ReassignmentRequest;

/**
 * Repositorio MongoDB para SolicitudReasignacion.
 */
@Repository
public interface ReassignmentRequestRepository extends MongoRepository<ReassignmentRequest, String> {

        /**
         * Devuelve todas las solicitudes PENDIENTES cuyo destino es el agente indicado.
         * Usado para la bandeja de solicitudes recibidas.
         */
        List<ReassignmentRequest> findByDestinationAgentIdAndStatus(
                        String destinationAgentId,
                        ReassignmentRequest.RequestStatus status);

        /**
         * Devuelve todas las solicitudes enviadas por un agente, sin filtro de status.
         * Útil para que el solicitante vea el historial de sus solicitudes.
         */
        List<ReassignmentRequest> findByRequestingAgentId(String requestingAgentId);

        /**
         * Devuelve solicitudes PENDIENTES asociadas a una cita específica.
         * Evita solicitudes duplicadas sobre la misma cita.
         */
        List<ReassignmentRequest> findByVisitIdAndStatus(
                        String visitId,
                        ReassignmentRequest.RequestStatus status);

        /**
         * Cuenta solicitudes PENDIENTES para el agente destino.
         * Usado para el badge de notificación en el menú.
         */
        long countByDestinationAgentIdAndStatus(
                        String destinationAgentId,
                        ReassignmentRequest.RequestStatus status);
}