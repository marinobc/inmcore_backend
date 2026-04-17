package com.inmobiliaria.access_control_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.inmobiliaria.access_control_service.security.UserContextFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final UserContextFilter userContextFilter;

  public SecurityConfig(UserContextFilter userContextFilter) {
    this.userContextFilter = userContextFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(userContextFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
