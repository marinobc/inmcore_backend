package com.inmobiliaria.api_gateway.filter;

import com.inmobiliaria.api_gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    @Autowired
    private JwtUtil jwtUtil;

    // Endpoints that do not require authentication
    private static final List<String> OPEN_ENDPOINTS = List.of(
            "/auth/login",
            "/auth/logout",
            "/auth/refresh",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/eureka"
    );

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
            
            boolean isSecured = OPEN_ENDPOINTS.stream().noneMatch(uri -> path.contains(uri));
            
            if (isSecured) {
                if (!exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                String authHeader = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
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
                    
                    exchange = exchange.mutate().request(
                        exchange.getRequest().mutate()
                            .header("X-Auth-User-Id", claims.getSubject())
                            .header("X-Auth-Roles", rolesString) // Clean string: "ADMIN,AGENT"
                            .build()
                    ).build();
                } catch (Exception e) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
            }
            return chain.filter(exchange);
        });
    }

    public static class Config {
    }
}
