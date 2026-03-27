package com.inmobiliaria.notification_service.dto.response;

import com.inmobiliaria.notification_service.domain.NotificationStatus;

public record NotificationResponse(
        String message,
        NotificationStatus status
) {
}