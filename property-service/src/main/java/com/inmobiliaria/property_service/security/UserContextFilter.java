package com.inmobiliaria.property_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-Auth-User-Id");
        String rolesHeader = request.getHeader("X-Auth-Roles");

        if (userId != null && rolesHeader != null) {
            // Limpiamos el header por si viene en formato [ROLE1, ROLE2]
            String cleanRoles = rolesHeader.replace("[", "").replace("]", "").replace(" ", "");

            if (!cleanRoles.isEmpty()) {
                List<SimpleGrantedAuthority> authorities = Arrays.stream(cleanRoles.split(","))
                        .filter(role -> !role.isEmpty())
                        // IMPORTANTE: Spring Security busca el prefijo ROLE_ por defecto
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()))
                        .collect(Collectors.toList());

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, authorities);

                // Establecemos la identidad del usuario en el contexto de Spring
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}