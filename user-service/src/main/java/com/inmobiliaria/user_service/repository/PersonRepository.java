package com.inmobiliaria.user_service.repository;

import com.inmobiliaria.user_service.domain.EmployeeDocument;
import com.inmobiliaria.user_service.domain.PersonDocument;
import com.inmobiliaria.user_service.domain.PersonType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonRepository extends MongoRepository<PersonDocument, String> {
    Optional<PersonDocument> findByAuthUserId(String authUserId);
    List<PersonDocument> findByPersonType(PersonType personType);
    boolean existsByAuthUserId(String authUserId);
    void deleteByAuthUserId(String authUserId);

    // Verifica CI duplicado entre owners
    @Query("{ 'taxId': ?0, '_class': 'owner' }")
    Optional<PersonDocument> findOwnerByTaxId(String taxId);

    // Verifica email duplicado entre todas las personas
    boolean existsByEmail(String email);

    @Query("{ 'authUserId': ?0, '_class': { $in: ['employee', 'com.inmobiliaria.user_service.domain.EmployeeDocument'] } }")
    Optional<EmployeeDocument> findEmployeeByAuthUserId(String authUserId);
}