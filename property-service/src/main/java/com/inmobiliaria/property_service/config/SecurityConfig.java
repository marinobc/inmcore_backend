package com.inmobiliaria.property_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.inmobiliaria.property_service.security.UserContextFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // <-- Esto activa el funcionamiento de @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

  private final UserContextFilter userContextFilter;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable()) // Desactivar CSRF para microservicios stateless
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated() // Todas las rutas requieren estar autenticado vía Gateway
            )
        // Añadimos nuestro filtro personalizado antes del filtro de autenticación estándar
        .addFilterBefore(userContextFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
