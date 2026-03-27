package com.inmobiliaria.property_service.security;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.inmobiliaria.property_service.repository.PropertyRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class PropertyPermissionInterceptor implements HandlerInterceptor {

    private final PropertyRepository propertyRepository;

    public PropertyPermissionInterceptor(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String uri = request.getRequestURI();
        // Esperamos ruta: /properties/{id}/images/upload
        String[] parts = uri.split("/");
        if (parts.length < 4) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Ruta de resource inválida");
            return false;
        }

        String propertyId = parts[2];
        if (propertyId == null || propertyId.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "ID de propiedad requerido");
            return false;
        }

        var propertyOpt = propertyRepository.findById(propertyId);
        if (propertyOpt.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Inmueble no encontrado");
            return false;
        }

        var property = propertyOpt.get();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No autenticado");
            return false;
        }

        String userId = String.valueOf(auth.getPrincipal());
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // Lógica de permisos por rol
        boolean isAdmin = roles.contains("ROLE_ADMIN");
        boolean isAgent = roles.contains("ROLE_AGENT");
        boolean isAssignedAgent = userId.equalsIgnoreCase(property.getAssignedAgentId());

        // Admin siempre tiene acceso
        if (isAdmin) {
            return true;
        }

        // Agente asignado tiene acceso
        if (isAssignedAgent) {
            return true;
        }

        // Agente general tiene acceso si está en la política de acceso
        boolean isGeneralAgentAllowed = isAgent && property.getAccessPolicy().contains("ROLE_AGENT");

        if (isGeneralAgentAllowed) {
            return true;
        }

        // Verificar políticas específicas de usuario o rol
        boolean isAllowedByPolicy = property.getAccessPolicy().stream().anyMatch(policy -> {
            String normalized = policy.trim();
            if (normalized.startsWith("ROLE_")) {
                return roles.contains(normalized);
            }
            return normalized.equalsIgnoreCase(userId);
        });

        if (isAllowedByPolicy) {
            return true;
        }

        response.sendError(HttpServletResponse.SC_FORBIDDEN, "No tienes permisos para generar URL prefirmada");
        return false;
    }
}
