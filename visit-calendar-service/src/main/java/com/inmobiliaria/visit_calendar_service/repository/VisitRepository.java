package com.inmobiliaria.visit_calendar_service.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.inmobiliaria.visit_calendar_service.model.Visit;

/**
 * Repositorio MongoDB para la entidad Visita.
 * Si ya existe en tu proyecto, agrega únicamente los métodos que falten.
 */
@Repository
public interface VisitRepository extends MongoRepository<Visit, String> {

    /** Todas las citas de un agente (para su agenda) */
    List<Visit> findByAgentId(String agentId);

    /** Citas de un agente en un rango de fechas */
    List<Visit> findByAgentIdAndDateTimeBetween(String agentId,
            LocalDateTime start,
            LocalDateTime end);

    /** Citas de un cliente */
    List<Visit> findByClientId(String clientId);

    /** Citas de un inmueble */
    List<Visit> findByPropertyId(String propertyId);
}