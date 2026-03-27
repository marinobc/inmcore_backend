package com.inmobiliaria.identity_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import lombok.Data;
import lombok.Builder;

@FeignClient(name = "notification-service")
public interface NotificationClient {

    @PostMapping("/notifications/credentials")
    void sendCredentialsEmail(@RequestBody CredentialsRequest request);

    @Data
    @Builder
    class CredentialsRequest {
        private String to;
        private String fullName;
        private String temporaryPassword;
    }
}
