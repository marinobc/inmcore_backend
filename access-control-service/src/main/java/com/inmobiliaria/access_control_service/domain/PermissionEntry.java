package com.inmobiliaria.access_control_service.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionEntry {

    @NotBlank
    private String resource;

    @NotBlank
    private String action;

    @NotNull
    private ScopeType scope;
}