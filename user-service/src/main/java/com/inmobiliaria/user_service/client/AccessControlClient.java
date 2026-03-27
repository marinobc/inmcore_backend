package com.inmobiliaria.user_service.client;

import com.inmobiliaria.user_service.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "access-control-service", configuration = FeignConfig.class)
public interface AccessControlClient {

    @GetMapping("/roles/validate")
    boolean validateRoleIds(@RequestParam("ids") List<String> ids);
}
