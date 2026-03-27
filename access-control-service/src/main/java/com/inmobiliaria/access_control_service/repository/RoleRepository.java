package com.inmobiliaria.access_control_service.repository;

import com.inmobiliaria.access_control_service.domain.RoleDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RoleRepository extends MongoRepository<RoleDocument, String> {

    Optional<RoleDocument> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByName(String name);
}