package com.inmobiliaria.identity_service.exception;

public class TemporaryPasswordExpiredException extends RuntimeException {

    public TemporaryPasswordExpiredException(String message) {
        super(message);
    }
}