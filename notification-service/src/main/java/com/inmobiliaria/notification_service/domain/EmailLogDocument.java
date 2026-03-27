package com.inmobiliaria.notification_service.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "email_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailLogDocument {

    @Id
    private String id;

    private String to;
    private String subject;
    private String body;
    private NotificationStatus status;
    private String errorMessage;
    private Instant createdAt;
}