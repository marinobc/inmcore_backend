package com.inmobiliaria.identity_service.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public abstract class BaseDocument {

    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
}