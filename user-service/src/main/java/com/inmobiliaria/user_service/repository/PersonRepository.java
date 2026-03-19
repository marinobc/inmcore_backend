package com.inmobiliaria.user_service.repository;

import com.inmobiliaria.user_service.domain.PersonDocument;
import com.inmobiliaria.user_service.domain.PersonType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonRepository extends MongoRepository<PersonDocument, String> {
    Optional<PersonDocument> findByAuthUserId(String authUserId);
    List<PersonDocument> findByPersonType(PersonType personType);
    boolean existsByAuthUserId(String authUserId);
    void deleteByAuthUserId(String authUserId);
}
