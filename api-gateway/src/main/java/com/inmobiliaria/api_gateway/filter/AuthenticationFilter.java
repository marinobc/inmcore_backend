package com.inmobiliaria.api_gateway.filter;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inmobiliaria.api_gateway.dto.ApiResponse;
import com.inmobiliaria.api_gateway.util.JwtUtil;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthenticationFilter
    extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

  @Autowired private JwtUtil jwtUtil;

  @Autowired private ObjectMapper objectMapper;

  // Endpoints that do not require authentication
  private static final List<String> OPEN_ENDPOINTS =
      List.of(
          "/api/v1/auth/login",
          "/api/v1/auth/logout",
          "/api/v1/auth/refresh",
          "/api/v1/auth/forgot-password",
          "/api/v1/auth/reset-password",
          "/eureka",
          "/v3/api-docs",
          "/swagger-ui",
          "/swagger-ui.html",
          "/webjars/swagger-ui");

  public AuthenticationFilter() {
    super(Config.class);
  }

  @Override
  public GatewayFilter apply(Config config) {
    return ((exchange, chain) -> {
      if (exchange.getRequest().getMethod() == org.springframework.http.HttpMethod.OPTIONS) {
        return chain.filter(exchange);
      }
      String path = exchange.getRequest().getURI().getPath();

      String serviceName = exchange.getRequest().getHeaders().getFirst("X-Service-Name");
      if (serviceName != null && !serviceName.isEmpty()) {
        return chain.filter(exchange);
      }

      boolean isSecured =
          OPEN_ENDPOINTS.stream().noneMatch(uri -> path.equals(uri) || path.startsWith(uri + "/"))
              && !path.contains("/v3/api-docs");

      if (isSecured) {
        if (!exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
          return onError(
              exchange, "Missing Authorization header", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        String authHeader =
            exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
          authHeader = authHeader.substring(7);
        }

        try {
          jwtUtil.validateToken(authHeader);
          Claims claims = jwtUtil.getClaims(authHeader);

          Object rolesObj = claims.get("roles");
          String rolesString = "";
          if (rolesObj instanceof List<?> rolesList) {
            rolesString = String.join(",", rolesList.stream().map(Object::toString).toList());
          }

          exchange =
              exchange
                  .mutate()
                  .request(
                      exchange
                          .getRequest()
                          .mutate()
                          .header("X-Auth-User-Id", claims.getSubject())
                          .header("X-Auth-Roles", rolesString)
                          .build())
                  .build();
        } catch (Exception e) {
          log.error("JWT Validation failed: {}", e.getMessage());
          return onError(
              exchange, "Invalid or expired token", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }
      }
      return chain.filter(exchange);
    });
  }

  private Mono<Void> onError(
      ServerWebExchange exchange, String message, String code, HttpStatus status) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    ApiResponse<Void> apiResponse = ApiResponse.error(message, code, message);

    try {
      byte[] bytes = objectMapper.writeValueAsBytes(apiResponse);
      DataBuffer buffer = response.bufferFactory().wrap(bytes);
      return response.writeWith(Mono.just(buffer));
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize error response", e);
      return response.setComplete();
    }
  }

  public static class Config {}
}
