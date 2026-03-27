package com.inmobiliaria.visit_calendar_service.exception;

/**
 * Excepción lanzada cuando se detecta un conflicto de horario
 * al intentar programar una visita en un inmueble ya reservado.
 */
public class ScheduleConflictException extends RuntimeException {

    public ScheduleConflictException(String message) {
        super(message);
    }
}
