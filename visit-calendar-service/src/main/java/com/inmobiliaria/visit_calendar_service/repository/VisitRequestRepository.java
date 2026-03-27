package com.inmobiliaria.visit_calendar_service.repository;

import com.inmobiliaria.visit_calendar_service.model.VisitRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio MongoDB para VisitRequest.
 * Cubre HU3: solicitudes de visita de clientes.
 */
@Repository
public interface VisitRequestRepository extends MongoRepository<VisitRequest, String> {

    /** Solicitudes pendientes para un agente específico */
    List<VisitRequest> findByAgentIdAndStatus(String agentId, VisitRequest.RequestStatus status);

    /** Todas las solicitudes de un agente */
    List<VisitRequest> findByAgentId(String agentId);

    /** Todas las solicitudes de un cliente */
    List<VisitRequest> findByClientId(String clientId);

    /** Solicitudes para una propiedad específica */
    List<VisitRequest> findByPropertyId(String propertyId);

    /** Solicitudes pendientes para una propiedad */
    List<VisitRequest> findByPropertyIdAndStatus(String propertyId, VisitRequest.RequestStatus status);
}
