package com.inmobiliaria.property_service.repository;

import com.inmobiliaria.property_service.domain.PropertyDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PropertyRepository extends MongoRepository<PropertyDocument, String> {
    List<PropertyDocument> findByAssignedAgentId(String assignedAgentId);
    List<PropertyDocument> findByOwnerId(String ownerId);
}