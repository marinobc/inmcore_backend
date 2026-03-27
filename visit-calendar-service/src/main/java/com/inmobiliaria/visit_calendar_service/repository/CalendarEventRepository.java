package com.inmobiliaria.visit_calendar_service.repository;

import com.inmobiliaria.visit_calendar_service.model.CalendarEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio MongoDB para CalendarEvent.
 * Contiene queries para el calendario compartido, filtros y detección de conflictos.
 */
@Repository
public interface CalendarEventRepository extends MongoRepository<CalendarEvent, String> {

    // --- Consultas para HU1: Visualizar calendario compartido ---

    /**
     * Obtiene todos los eventos en un rango de fechas (vista semanal/mensual).
     */
    @Query("{ 'startTime': { $gte: ?0 }, 'endTime': { $lte: ?1 }, 'status': { $ne: 'CANCELLED' } }")
    List<CalendarEvent> findByDateRange(LocalDateTime from, LocalDateTime to);

    /**
     * Obtiene eventos de un agente específico en un rango de fechas.
     */
    @Query("{ 'agentId': ?0, 'startTime': { $gte: ?1 }, 'endTime': { $lte: ?2 }, 'status': { $ne: 'CANCELLED' } }")
    List<CalendarEvent> findByAgentIdAndDateRange(String agentId, LocalDateTime from, LocalDateTime to);

    /**
     * Obtiene todos los eventos de una propiedad específica.
     */
    @Query("{ 'propertyId': ?0, 'status': { $ne: 'CANCELLED' } }")
    List<CalendarEvent> findByPropertyId(String propertyId);

    /**
     * Obtiene eventos de una propiedad en un rango de fechas.
     */
    @Query("{ 'propertyId': ?0, 'startTime': { $gte: ?1 }, 'endTime': { $lte: ?2 }, 'status': { $ne: 'CANCELLED' } }")
    List<CalendarEvent> findByPropertyIdAndDateRange(String propertyId, LocalDateTime from, LocalDateTime to);

    // --- Consultas para HU2: Detección de conflictos de horario ---

    /**
     * Detecta si existe un conflicto de horario para una propiedad específica.
     * Un conflicto ocurre cuando el nuevo evento se solapa con uno existente:
     *   - El nuevo evento empieza ANTES de que termine uno existente, Y
     *   - El nuevo evento termina DESPUÉS de que empiece uno existente.
     * Se excluye el evento con excludeId para poder hacer actualizaciones sin falso positivo.
     */
    @Query("{ " +
           "  'propertyId': ?0, " +
           "  'status': { $nin: ['CANCELLED'] }, " +
           "  '_id': { $ne: ?3 }, " +
           "  '$or': [ " +
           "    { 'startTime': { $lt: ?2 }, 'endTime': { $gt: ?1 } } " +
           "  ] " +
           "}")
    List<CalendarEvent> findConflictingEvents(
            String propertyId,
            LocalDateTime newStart,
            LocalDateTime newEnd,
            String excludeId
    );

    /**
     * Versión sin excludeId para nuevos eventos (sin ID todavía).
     */
    @Query("{ " +
           "  'propertyId': ?0, " +
           "  'status': { $nin: ['CANCELLED'] }, " +
           "  '$or': [ " +
           "    { 'startTime': { $lt: ?2 }, 'endTime': { $gt: ?1 } } " +
           "  ] " +
           "}")
    List<CalendarEvent> findConflictingEventsForNew(
            String propertyId,
            LocalDateTime newStart,
            LocalDateTime newEnd
    );

    // --- Consultas generales ---

    List<CalendarEvent> findByAgentId(String agentId);

    @Query("{ 'startTime': { $gte: ?0, $lt: ?1 }, 'agentId': ?2, 'status': { $ne: 'CANCELLED' } }")
    List<CalendarEvent> findByDayAndAgent(LocalDateTime dayStart, LocalDateTime dayEnd, String agentId);
}
