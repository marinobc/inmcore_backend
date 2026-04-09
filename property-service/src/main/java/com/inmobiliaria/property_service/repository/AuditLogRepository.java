package com.inmobiliaria.property_service.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.inmobiliaria.property_service.domain.AuditLog;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findByResourceIdOrderByTimestampDesc(String resourceId);
}