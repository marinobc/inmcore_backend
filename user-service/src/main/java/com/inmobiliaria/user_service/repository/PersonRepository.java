package com.inmobiliaria.user_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.inmobiliaria.user_service.domain.EmployeeDocument;
import com.inmobiliaria.user_service.domain.InterestedClientDocument;
import com.inmobiliaria.user_service.domain.PersonDocument;
import com.inmobiliaria.user_service.domain.PersonType;

@Repository
public interface PersonRepository extends MongoRepository<PersonDocument, String> {
  Optional<PersonDocument> findByAuthUserId(String authUserId);

  List<PersonDocument> findByPersonType(PersonType personType);

  boolean existsByAuthUserId(String authUserId);

  void deleteByAuthUserId(String authUserId);

  // Verifica CI duplicado entre owners
  @Query(
      "{ 'taxId': ?0, '_class': { $in: ['owner', 'com.inmobiliaria.user_service.domain.OwnerDocument'] } }")
  Optional<PersonDocument> findOwnerByTaxId(String taxId);

  @Query("{ 'taxId': ?0 }")
  Optional<PersonDocument> findByTaxId(String taxId);

  // Verifica email duplicado entre todas las personas
  boolean existsByEmail(String email);

  @Query(
      "{ 'authUserId': ?0, '_class': { $in: ['employee', 'com.inmobiliaria.user_service.domain.EmployeeDocument'] } }")
  Optional<EmployeeDocument> findEmployeeByAuthUserId(String authUserId);

  @Query("{ 'assignedClientIds': ?0 }")
  List<EmployeeDocument> findByAssignedClientId(String clientId);

  @Query("{ 'assignedOwnerIds': ?0 }")
  List<EmployeeDocument> findByAssignedOwnerId(String ownerId);

  // Obtiene clientes inactivos después de una fecha límite
  @Query(
      "{ '_class': { $in: ['interested_client', 'com.inmobiliaria.user_service.domain.InterestedClientDocument'] }, "
          + "'activo': true, "
          + "'$or': [ {'lastActivityDate': {$lt: ?0}}, {'lastActivityDate': null} ] }")
  List<InterestedClientDocument> findClientesInactivosDespuesDe(java.time.LocalDate fechaLimite);
}
