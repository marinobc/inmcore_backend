package com.inmobiliaria.notification_service.controller;

import com.inmobiliaria.notification_service.dto.request.SendCredentialsEmailRequest;
import com.inmobiliaria.notification_service.dto.response.NotificationResponse;
import com.inmobiliaria.notification_service.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/credentials")
    @ResponseStatus(HttpStatus.OK)
    public NotificationResponse sendCredentials(@Valid @RequestBody SendCredentialsEmailRequest request) {
        return notificationService.sendCredentialsEmail(request);
    }
}