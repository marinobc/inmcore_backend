package com.inmobiliaria.identity_service.repository;

import com.inmobiliaria.identity_service.domain.UserDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<UserDocument, String> {

    Optional<UserDocument> findByEmailNormalized(String emailNormalized);

    Optional<UserDocument> findByRefreshToken(String refreshToken);

    boolean existsByEmailNormalized(String emailNormalized);
}