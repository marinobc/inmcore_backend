package com.inmobiliaria.notification_service.repository;

import com.inmobiliaria.notification_service.domain.EmailLogDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmailLogRepository extends MongoRepository<EmailLogDocument, String> {
}