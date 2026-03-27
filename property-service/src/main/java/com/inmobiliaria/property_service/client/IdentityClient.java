package com.inmobiliaria.property_service.client;

import com.inmobiliaria.property_service.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "identity-service", configuration = FeignConfig.class)
public interface IdentityClient {

    @GetMapping("/users/{id}")
    UserResponse findById(@PathVariable("id") String id);

    record UserResponse(String id, String status) {}
}