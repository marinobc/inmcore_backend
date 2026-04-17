package com.inmobiliaria.user_service.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.inmobiliaria.user_service.client.AccessControlClient;
import com.inmobiliaria.user_service.domain.AdminDocument;
import com.inmobiliaria.user_service.domain.AuditEntry;
import com.inmobiliaria.user_service.domain.AuditLogDocument;
import com.inmobiliaria.user_service.domain.EmployeeDocument;
import com.inmobiliaria.user_service.domain.InterestedClientDocument;
import com.inmobiliaria.user_service.domain.OwnerDocument;
import com.inmobiliaria.user_service.domain.PersonDocument;
import com.inmobiliaria.user_service.domain.PersonType;
import com.inmobiliaria.user_service.dto.request.CreateInterestedClientRequest;
import com.inmobiliaria.user_service.dto.request.CreatePersonRequest;
import com.inmobiliaria.user_service.dto.request.UpdatePersonRequest;
import com.inmobiliaria.user_service.dto.response.PersonResponse;
import com.inmobiliaria.user_service.exception.ResourceAlreadyExistsException;
import com.inmobiliaria.user_service.exception.ResourceNotFoundException;
import com.inmobiliaria.user_service.repository.AuditLogRepository;
import com.inmobiliaria.user_service.repository.PersonRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonService {

  private final PersonRepository personRepository;
  private final AccessControlClient accessControlClient;
  private final AuditLogRepository auditLogRepository;
  private final MongoTemplate mongoTemplate;

  public PersonResponse create(CreatePersonRequest request) {
    log.info("Creating person profile for authUserId: {}", request.authUserId());

    if (personRepository.existsByAuthUserId(request.authUserId())) {
      throw new ResourceAlreadyExistsException(
          "Profile already exists for authUserId: " + request.authUserId());
    }

    if (request.roleIds() != null && !request.roleIds().isEmpty()) {
      boolean validRoles = accessControlClient.validateRoleIds(request.roleIds());
      if (!validRoles) {
        throw new ResourceNotFoundException("One or more role IDs are invalid");
      }
    }

    PersonDocument document;
    switch (request.personType()) {
      case ADMIN ->
          document =
              AdminDocument.builder()
                  .authUserId(request.authUserId())
                  .firstName(request.firstName())
                  .lastName(request.lastName())
                  .fullName(request.firstName() + " " + request.lastName())
                  .birthDate(request.birthDate())
                  .phone(request.phone())
                  .email(request.email())
                  .roleIds(request.roleIds())
                  .build();
      case EMPLOYEE ->
          document =
              EmployeeDocument.builder()
                  .authUserId(request.authUserId())
                  .firstName(request.firstName())
                  .lastName(request.lastName())
                  .fullName(request.firstName() + " " + request.lastName())
                  .birthDate(request.birthDate())
                  .phone(request.phone())
                  .email(request.email())
                  .roleIds(request.roleIds())
                  .department(request.department())
                  .position(request.position())
                  .hireDate(request.hireDate())
                  .build();
      case OWNER ->
          document =
              OwnerDocument.builder()
                  .authUserId(request.authUserId())
                  .firstName(request.firstName())
                  .lastName(request.lastName())
                  .fullName(request.firstName() + " " + request.lastName())
                  .birthDate(request.birthDate())
                  .phone(request.phone())
                  .email(request.email())
                  .roleIds(request.roleIds())
                  .taxId(request.taxId())
                  .address(request.address())
                  .propertyIds(request.propertyIds())
                  .build();
      case INTERESTED_CLIENT ->
          document =
              InterestedClientDocument.builder()
                  .authUserId(request.authUserId())
                  .firstName(request.firstName())
                  .lastName(request.lastName())
                  .fullName(request.firstName() + " " + request.lastName())
                  .birthDate(request.birthDate())
                  .phone(request.phone())
                  .email(request.email())
                  .roleIds(request.roleIds())
                  .preferredContactMethod(request.preferredContactMethod())
                  .budget(request.budget())
                  .preferredZone(request.preferredZone())
                  .preferredPropertyType(request.preferredPropertyType())
                  .preferredRooms(request.preferredRooms())
                  .build();
      default ->
          throw new IllegalArgumentException("Unsupported person type: " + request.personType());
    }

    document.setCreatedAt(Instant.now());
    document.setUpdatedAt(Instant.now());
    document.setCreatedBy("system");

    PersonDocument saved = personRepository.save(document);

    auditLogRepository.save(
        AuditLogDocument.builder()
            .timestamp(Instant.now())
            .changedBy(getCurrentUserId())
            .action("CREATED")
            .personId(saved.getId())
            .personName(saved.getFullName())
            .personType(saved.getPersonType().name())
            .changes(null)
            .build());

    // --- ASIGNAR CLIENTE/PROPIETARIO AL AGENTE SI SE PROPORCIONÓ assignedAgentId ---
    if (request.assignedAgentId() != null
        && (request.personType() == PersonType.INTERESTED_CLIENT
            || request.personType() == PersonType.OWNER)) {
      Optional<EmployeeDocument> agentOpt =
          personRepository.findEmployeeByAuthUserId(request.assignedAgentId());
      if (agentOpt.isEmpty()) {
        log.warn(
            "Agent EmployeeDocument not found for authUserId: {}. Person created without assignment.",
            request.assignedAgentId());
      } else {
        EmployeeDocument agent = agentOpt.get();
        if (request.personType() == PersonType.INTERESTED_CLIENT) {
          if (agent.getAssignedClientIds() == null) {
            agent.setAssignedClientIds(new ArrayList<>());
          }
          if (!agent.getAssignedClientIds().contains(saved.getId())) {
            agent.getAssignedClientIds().add(saved.getId());
          }
        } else {
          if (agent.getAssignedOwnerIds() == null) {
            agent.setAssignedOwnerIds(new ArrayList<>());
          }
          if (!agent.getAssignedOwnerIds().contains(saved.getId())) {
            agent.getAssignedOwnerIds().add(saved.getId());
          }
        }
        personRepository.save(agent);
        log.info("{} {} assigned to agent {}", request.personType(), saved.getId(), agent.getId());
      }
    }

    return mapToResponse(saved);
  }

  public Page<PersonResponse> findAll(String type, Boolean activo, Pageable pageable) {
    List<PersonDocument> all;

    if (type != null && !type.isBlank()) {
      try {
        PersonType personType = PersonType.valueOf(type.toUpperCase());
        all = personRepository.findByPersonType(personType);
      } catch (IllegalArgumentException e) {
        log.warn("Invalid person type: {}", type);
        all = personRepository.findAll();
      }
    } else {
      all = personRepository.findAll();
    }

    Stream<PersonDocument> stream = all.stream();

    if (activo != null) {
      stream =
          stream.filter(
              p -> {
                if (p instanceof InterestedClientDocument client) {
                  return client.isActivo() == activo;
                }
                return true; // Solo InterestedClientDocument tiene el campo activo en este modelo
              });
    }

    List<PersonResponse> filtered = stream.map(this::mapToResponse).collect(Collectors.toList());

    int start = (int) pageable.getOffset();
    int end = Math.min((start + pageable.getPageSize()), filtered.size());

    if (start > filtered.size()) {
      return new PageImpl<>(List.of(), pageable, filtered.size());
    }

    return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
  }

  public PersonResponse findById(String id) {
    return personRepository
        .findById(id)
        .map(this::mapToResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Person not found with id: " + id));
  }

  public PersonResponse findByAuthUserId(String authUserId) {
    return personRepository
        .findByAuthUserId(authUserId)
        .map(this::mapToResponse)
        .orElseThrow(
            () -> new ResourceNotFoundException("Person not found with authUserId: " + authUserId));
  }

  public PersonResponse findPersonByTaxId(String taxId) {
    return personRepository
        .findByTaxId(taxId)
        .map(this::mapToResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Person not found with taxId: " + taxId));
  }

  public PersonResponse update(String id, UpdatePersonRequest request) {
    PersonDocument person =
        personRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Person not found with id: " + id));

    if (request.taxId() != null && !request.taxId().isBlank()) {
      Optional<PersonDocument> existing = personRepository.findByTaxId(request.taxId());
      if (existing.isPresent() && !existing.get().getId().equals(id)) {
        throw new ResourceAlreadyExistsException(
            "A person with CI/NIT " + request.taxId() + " already exists.");
      }
    }

    List<AuditEntry.FieldChange> changes = new ArrayList<>();

    if (request.firstName() != null && !request.firstName().equals(person.getFirstName())) {
      changes.add(
          AuditEntry.FieldChange.builder()
              .field("firstName")
              .oldValue(person.getFirstName())
              .newValue(request.firstName())
              .build());
      person.setFirstName(request.firstName());
    }
    if (request.lastName() != null && !request.lastName().equals(person.getLastName())) {
      changes.add(
          AuditEntry.FieldChange.builder()
              .field("lastName")
              .oldValue(person.getLastName())
              .newValue(request.lastName())
              .build());
      person.setLastName(request.lastName());
    }
    if (person.getFirstName() != null && person.getLastName() != null) {
      person.setFullName(person.getFirstName() + " " + person.getLastName());
    }
    if (request.birthDate() != null && !request.birthDate().equals(person.getBirthDate())) {
      changes.add(
          AuditEntry.FieldChange.builder()
              .field("birthDate")
              .oldValue(person.getBirthDate() != null ? person.getBirthDate().toString() : null)
              .newValue(request.birthDate().toString())
              .build());
      person.setBirthDate(request.birthDate());
    }
    if (request.phone() != null && !request.phone().equals(person.getPhone())) {
      changes.add(
          AuditEntry.FieldChange.builder()
              .field("phone")
              .oldValue(person.getPhone())
              .newValue(request.phone())
              .build());
      person.setPhone(request.phone());
    }

    if (person instanceof EmployeeDocument emp) {
      if (request.department() != null && !request.department().equals(emp.getDepartment())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("department")
                .oldValue(emp.getDepartment())
                .newValue(request.department())
                .build());
        emp.setDepartment(request.department());
      }
      if (request.position() != null && !request.position().equals(emp.getPosition())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("position")
                .oldValue(emp.getPosition())
                .newValue(request.position())
                .build());
        emp.setPosition(request.position());
      }
      if (request.hireDate() != null && !request.hireDate().equals(emp.getHireDate())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("hireDate")
                .oldValue(emp.getHireDate() != null ? emp.getHireDate().toString() : null)
                .newValue(request.hireDate().toString())
                .build());
        emp.setHireDate(request.hireDate());
      }
    } else if (person instanceof OwnerDocument owner) {
      if (request.taxId() != null && !request.taxId().equals(owner.getTaxId())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("taxId")
                .oldValue(owner.getTaxId())
                .newValue(request.taxId())
                .build());
        owner.setTaxId(request.taxId());
      }
      if (request.address() != null && !request.address().equals(owner.getAddress())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("address")
                .oldValue(owner.getAddress())
                .newValue(request.address())
                .build());
        owner.setAddress(request.address());
      }
      if (request.propertyIds() != null && !request.propertyIds().equals(owner.getPropertyIds())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("propertyIds")
                .oldValue(owner.getPropertyIds() != null ? owner.getPropertyIds().toString() : null)
                .newValue(request.propertyIds().toString())
                .build());
        owner.setPropertyIds(request.propertyIds());
      }
    } else if (person instanceof InterestedClientDocument client) {
      if (request.preferredContactMethod() != null
          && !request.preferredContactMethod().equals(client.getPreferredContactMethod())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("preferredContactMethod")
                .oldValue(client.getPreferredContactMethod())
                .newValue(request.preferredContactMethod())
                .build());
        client.setPreferredContactMethod(request.preferredContactMethod());
      }
      if (request.budget() != null && !request.budget().equals(client.getBudget())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("budget")
                .oldValue(client.getBudget())
                .newValue(request.budget())
                .build());
        client.setBudget(request.budget());
      }
      if (request.preferredZone() != null
          && !request.preferredZone().equals(client.getPreferredZone())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("preferredZone")
                .oldValue(client.getPreferredZone())
                .newValue(request.preferredZone())
                .build());
        client.setPreferredZone(request.preferredZone());
      }
      if (request.preferredPropertyType() != null
          && !request.preferredPropertyType().equals(client.getPreferredPropertyType())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("preferredPropertyType")
                .oldValue(client.getPreferredPropertyType())
                .newValue(request.preferredPropertyType())
                .build());
        client.setPreferredPropertyType(request.preferredPropertyType());
      }
      if (request.preferredRooms() != null
          && !request.preferredRooms().equals(client.getPreferredRooms())) {
        changes.add(
            AuditEntry.FieldChange.builder()
                .field("preferredRooms")
                .oldValue(
                    client.getPreferredRooms() != null
                        ? client.getPreferredRooms().toString()
                        : null)
                .newValue(request.preferredRooms().toString())
                .build());
        client.setPreferredRooms(request.preferredRooms());
      }
    }

    if (!changes.isEmpty()) {
      String changedBy = getCurrentUserId();

      auditLogRepository.save(
          AuditLogDocument.builder()
              .timestamp(Instant.now())
              .changedBy(changedBy)
              .action("UPDATED")
              .personId(person.getId())
              .personName(person.getFullName())
              .personType(person.getPersonType().name())
              .changes(changes)
              .build());

      AuditEntry entry =
          AuditEntry.builder()
              .changedAt(Instant.now())
              .changedBy(changedBy)
              .changes(changes)
              .build();

      if (person.getAuditLog() == null) person.setAuditLog(new ArrayList<>());
      person.getAuditLog().add(entry);
    }

    person.setUpdatedAt(Instant.now());
    return mapToResponse(personRepository.save(person));
  }

  public PersonResponse updateByAuthUserId(String authUserId, UpdatePersonRequest request) {
    PersonDocument person =
        personRepository
            .findByAuthUserId(authUserId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Person not found with authUserId: " + authUserId));
    return update(person.getId(), request);
  }

  public PersonResponse assignRoles(String id, List<String> roleIds, boolean isCustom) {
    PersonDocument person =
        personRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Person not found with id: " + id));

    boolean validRoles = accessControlClient.validateRoleIds(roleIds);
    if (!validRoles) {
      throw new ResourceNotFoundException("One or more role IDs are invalid");
    }

    person.setRoleIds(roleIds);
    person.setCustomRole(isCustom);
    person.setUpdatedAt(Instant.now());

    return mapToResponse(personRepository.save(person));
  }

  public void deleteById(String id) {
    if (!personRepository.existsById(id)) {
      throw new ResourceNotFoundException("Person not found with id: " + id);
    }
    personRepository.deleteById(id);
  }

  public void deleteByAuthUserId(String authUserId) {
    if (!personRepository.existsByAuthUserId(authUserId)) {
      throw new ResourceNotFoundException("Person not found with authUserId: " + authUserId);
    }
    personRepository.deleteByAuthUserId(authUserId);
  }

  public PersonResponse darDeBaja(String personId, String motivo, String changedBy) {
    PersonDocument person =
        personRepository
            .findById(personId)
            .orElseThrow(() -> new ResourceNotFoundException("Person not found: " + personId));

    if (!(person instanceof InterestedClientDocument client)) {
      throw new IllegalArgumentException("Solo se puede dar de baja a clientes interesados");
    }
    if (!client.isActivo()) {
      throw new IllegalStateException("El cliente ya está dado de baja");
    }

    client.setActivo(false);
    client.setFechaBaja(java.time.LocalDate.now());
    client.setMotivoBaja(motivo);

    PersonDocument saved = personRepository.save(client);

    auditLogRepository.save(
        AuditLogDocument.builder()
            .timestamp(java.time.Instant.now())
            .changedBy(changedBy)
            .action("BAJA")
            .personId(saved.getId())
            .personName(saved.getFullName())
            .personType("INTERESTED_CLIENT")
            .changes(
                List.of(
                    new AuditEntry.FieldChange("activo", "true", "false"),
                    new AuditEntry.FieldChange("motivoBaja", null, motivo)))
            .build());

    return mapToResponse(saved);
  }

  public List<PersonResponse> findClientesInactivos(java.time.LocalDate fechaLimite) {
    return personRepository.findClientesInactivosDespuesDe(fechaLimite).stream()
        .map(this::mapToResponse)
        .collect(java.util.stream.Collectors.toList());
  }

  public List<PersonResponse> getClientsForAgent(String agentId) {
    EmployeeDocument agent =
        personRepository
            .findEmployeeByAuthUserId(agentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Agent not found with id: " + agentId));

    List<String> clientIds = agent.getAssignedClientIds();
    if (clientIds == null || clientIds.isEmpty()) {
      return List.of();
    }

    List<PersonDocument> clients = personRepository.findAllById(clientIds);
    return clients.stream()
        .filter(c -> c instanceof InterestedClientDocument)
        .map(this::mapToResponse)
        .collect(Collectors.toList());
  }

  // --- NEW ENDPOINT 1: Agent -> Clients ---
  public List<PersonResponse> getRelatedClientsForAgent(String agentId) {
    java.util.Set<String> clientAuthIds = new java.util.HashSet<>();

    // 1. Currently assigned to the agent (using internal _ids)
    Optional<EmployeeDocument> agentOpt = personRepository.findEmployeeByAuthUserId(agentId);
    if (agentOpt.isPresent()) {
      List<String> assignedIds = agentOpt.get().getAssignedClientIds();
      if (assignedIds != null && !assignedIds.isEmpty()) {
        List<PersonDocument> assignedDocs = personRepository.findAllById(assignedIds);
        assignedDocs.forEach(d -> clientAuthIds.add(d.getAuthUserId()));
      }
    }

    // 2. Have at least one appointment with the agent (agentId in visits is authUserId)
    Query visitQuery = new Query(Criteria.where("agentId").is(agentId));
    visitQuery.fields().include("clientId");
    List<org.bson.Document> visits =
        mongoTemplate.find(visitQuery, org.bson.Document.class, "visits");
    for (org.bson.Document v : visits) {
      String clientId = v.getString("clientId");
      if (clientId != null) clientAuthIds.add(clientId);
    }

    // 3. Requested appointment with the agent (visit_requests)
    List<org.bson.Document> requests =
        mongoTemplate.find(visitQuery, org.bson.Document.class, "visit_requests");
    for (org.bson.Document r : requests) {
      String clientId = r.getString("clientId");
      if (clientId != null) clientAuthIds.add(clientId);
    }

    if (clientAuthIds.isEmpty()) return List.of();

    Query query = new Query(Criteria.where("authUserId").in(clientAuthIds));
    List<PersonDocument> allClients = mongoTemplate.find(query, PersonDocument.class, "persons");
    return allClients.stream()
        .filter(c -> c instanceof InterestedClientDocument)
        .map(this::mapToResponse)
        .collect(Collectors.toList());
  }

  // --- NEW ENDPOINT 2: Agent -> Property Owners ---
  public List<PersonResponse> getRelatedOwnersForAgent(String agentId) {
    java.util.Set<String> ownerAuthIds = new java.util.HashSet<>();

    // 1. Directly assigned to the agent
    Optional<EmployeeDocument> agentOpt = personRepository.findEmployeeByAuthUserId(agentId);
    if (agentOpt.isPresent()) {
      List<String> assignedIds = agentOpt.get().getAssignedOwnerIds();
      if (assignedIds != null && !assignedIds.isEmpty()) {
        List<PersonDocument> assignedDocs = personRepository.findAllById(assignedIds);
        assignedDocs.forEach(d -> ownerAuthIds.add(d.getAuthUserId()));
      }
    }

    // 2. Have properties assigned to the agent (assignedAgentId is authUserId)
    Query propQuery = new Query(Criteria.where("assignedAgentId").is(agentId));
    propQuery.fields().include("ownerId");
    List<org.bson.Document> properties =
        mongoTemplate.find(propQuery, org.bson.Document.class, "properties");
    for (org.bson.Document p : properties) {
      String ownerId = p.getString("ownerId");
      if (ownerId != null) ownerAuthIds.add(ownerId);
    }

    // 2. Have properties with at least one appointment involving the agent
    Query visitQuery = new Query(Criteria.where("agentId").is(agentId));
    visitQuery.fields().include("propertyId");
    List<org.bson.Document> visits =
        mongoTemplate.find(visitQuery, org.bson.Document.class, "visits");
    java.util.Set<String> propertyIdsFromVisits = new java.util.HashSet<>();
    for (org.bson.Document v : visits) {
      String propId = v.getString("propertyId");
      if (propId != null) propertyIdsFromVisits.add(propId);
    }

    if (!propertyIdsFromVisits.isEmpty()) {
      Query propsFromVisitsQuery = new Query(Criteria.where("_id").in(propertyIdsFromVisits));
      propsFromVisitsQuery.fields().include("ownerId");
      List<org.bson.Document> propsFromVisits =
          mongoTemplate.find(propsFromVisitsQuery, org.bson.Document.class, "properties");
      for (org.bson.Document p : propsFromVisits) {
        String ownerId = p.getString("ownerId");
        if (ownerId != null) ownerAuthIds.add(ownerId);
      }
    }

    if (ownerAuthIds.isEmpty()) return List.of();

    Query query = new Query(Criteria.where("authUserId").in(ownerAuthIds));
    List<PersonDocument> allOwners = mongoTemplate.find(query, PersonDocument.class, "persons");
    return allOwners.stream()
        .filter(c -> c instanceof OwnerDocument)
        .map(this::mapToResponse)
        .collect(Collectors.toList());
  }

  public void updateLastActivityDate(String authUserId) {
    PersonDocument person =
        personRepository
            .findByAuthUserId(authUserId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Cliente no encontrado con authUserId: " + authUserId));
    if (person instanceof InterestedClientDocument client) {
      client.setLastActivityDate(java.time.LocalDate.now());
      personRepository.save(client);
    }
  }

  public void validarClienteActivo(String authUserId) {
    PersonDocument person =
        personRepository
            .findByAuthUserId(authUserId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Cliente no encontrado con authUserId: " + authUserId));
    if (person instanceof InterestedClientDocument client) {
      if (!client.isActivo()) {
        throw new IllegalStateException(
            "El cliente con id " + authUserId + " está dado de baja y no puede operar.");
      }
      client.setLastActivityDate(java.time.LocalDate.now());
      personRepository.save(client);
    } else {
      throw new IllegalStateException(
          "El usuario con authUserId " + authUserId + " no es un cliente interesado.");
    }
  }

  public PersonResponse createClientForAgent(
      String agentId, CreateInterestedClientRequest request) {
    EmployeeDocument agent =
        personRepository
            .findEmployeeByAuthUserId(agentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Agent not found with id: " + agentId));

    InterestedClientDocument client =
        InterestedClientDocument.builder()
            .authUserId(request.authUserId())
            .firstName(request.firstName())
            .lastName(request.lastName())
            .fullName(request.firstName() + " " + request.lastName())
            .birthDate(request.birthDate())
            .phone(request.phone())
            .email(request.email())
            .roleIds(List.of("rol_interested_client"))
            .preferredContactMethod(request.preferredContactMethod())
            .budget(request.budget())
            .build();

    client.setCreatedAt(Instant.now());
    client.setUpdatedAt(Instant.now());
    client.setCreatedBy(agentId);

    client = personRepository.save(client);

    if (agent.getAssignedClientIds() == null) {
      agent.setAssignedClientIds(new ArrayList<>());
    }
    agent.getAssignedClientIds().add(client.getId());
    personRepository.save(agent);

    return mapToResponse(client);
  }

  public PersonResponse updateClientForAgent(
      String agentId, String clientId, UpdatePersonRequest request) {
    EmployeeDocument agent =
        personRepository
            .findEmployeeByAuthUserId(agentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Agent not found with id: " + agentId));

    if (agent.getAssignedClientIds() == null || !agent.getAssignedClientIds().contains(clientId)) {
      throw new ResourceNotFoundException("Client not found or not assigned to you");
    }

    PersonDocument client =
        personRepository
            .findById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

    if (request.firstName() != null) client.setFirstName(request.firstName());
    if (request.lastName() != null) client.setLastName(request.lastName());
    if (client.getFirstName() != null && client.getLastName() != null) {
      client.setFullName(client.getFirstName() + " " + client.getLastName());
    }
    if (request.birthDate() != null) client.setBirthDate(request.birthDate());
    if (request.phone() != null) client.setPhone(request.phone());

    if (client instanceof InterestedClientDocument interested) {
      if (request.preferredContactMethod() != null)
        interested.setPreferredContactMethod(request.preferredContactMethod());
      if (request.budget() != null) interested.setBudget(request.budget());
      if (request.preferredZone() != null) interested.setPreferredZone(request.preferredZone());
      if (request.preferredPropertyType() != null)
        interested.setPreferredPropertyType(request.preferredPropertyType());
      if (request.preferredRooms() != null) interested.setPreferredRooms(request.preferredRooms());
    }

    // --- MANEJAR REASIGNACIÓN DE AGENTE ---
    if (request.assignedAgentId() != null) {
      String newAgentAuthId = request.assignedAgentId();

      // 1. Buscar nuevo agente
      Optional<EmployeeDocument> newAgentOpt =
          personRepository.findEmployeeByAuthUserId(newAgentAuthId);

      if (newAgentOpt.isPresent()) {
        EmployeeDocument newAgent = newAgentOpt.get();

        // 2. Determinar si es cliente o propietario
        boolean isClient = client instanceof InterestedClientDocument;
        boolean isOwner = client instanceof OwnerDocument;

        if (isClient || isOwner) {
          // 3. Remover de agentes anteriores para mantener integridad
          List<EmployeeDocument> previousAgents =
              isClient
                  ? personRepository.findByAssignedClientId(client.getId())
                  : personRepository.findByAssignedOwnerId(client.getId());

          for (EmployeeDocument prev : previousAgents) {
            if (!prev.getAuthUserId().equals(newAgentAuthId)) {
              if (isClient) {
                prev.getAssignedClientIds().remove(client.getId());
              } else {
                prev.getAssignedOwnerIds().remove(client.getId());
              }
              personRepository.save(prev);
            }
          }

          // 4. Agregar al nuevo agente
          if (isClient) {
            if (newAgent.getAssignedClientIds() == null)
              newAgent.setAssignedClientIds(new ArrayList<>());
            if (!newAgent.getAssignedClientIds().contains(client.getId())) {
              newAgent.getAssignedClientIds().add(client.getId());
            }
          } else {
            if (newAgent.getAssignedOwnerIds() == null)
              newAgent.setAssignedOwnerIds(new ArrayList<>());
            if (!newAgent.getAssignedOwnerIds().contains(client.getId())) {
              newAgent.getAssignedOwnerIds().add(client.getId());
            }
          }
          personRepository.save(newAgent);
          log.info("Person {} reassigned to agent {}", client.getId(), newAgent.getId());
        }
      }
    }

    client.setUpdatedAt(Instant.now());
    return mapToResponse(personRepository.save(client));
  }

  private String getCurrentUserId() {
    try {
      jakarta.servlet.http.HttpServletRequest request =
          ((org.springframework.web.context.request.ServletRequestAttributes)
                  org.springframework.web.context.request.RequestContextHolder
                      .getRequestAttributes())
              .getRequest();
      String userId = request.getHeader("X-Auth-User-Id");
      return userId != null ? userId : "unknown";
    } catch (Exception e) {
      return "unknown";
    }
  }

  private PersonResponse mapToResponse(PersonDocument document) {
    String dept = null,
        pos = null,
        tax = null,
        address = null,
        contact = null,
        budget = null,
        preferredZone = null,
        preferredPropertyType = null;
    List<String> propertyIds = null;
    LocalDate hire = null;
    Integer preferredRooms = null;

    if (document instanceof EmployeeDocument emp) {
      dept = emp.getDepartment();
      pos = emp.getPosition();
      hire = emp.getHireDate();
    } else if (document instanceof OwnerDocument owner) {
      tax = owner.getTaxId();
      address = owner.getAddress();
      propertyIds = owner.getPropertyIds();
    } else if (document instanceof InterestedClientDocument client) {
      contact = client.getPreferredContactMethod();
      budget = client.getBudget();
      preferredZone = client.getPreferredZone();
      preferredPropertyType = client.getPreferredPropertyType();
      preferredRooms = client.getPreferredRooms();
    }

    return new PersonResponse(
        document.getId(),
        document.getAuthUserId(),
        document.getFirstName(),
        document.getLastName(),
        document.getFullName(),
        document.getBirthDate(),
        document.getPhone(),
        document.getEmail(),
        document.getPersonType(),
        document.getRoleIds(),
        document.isCustomRole(),
        dept,
        pos,
        hire,
        tax,
        address,
        propertyIds,
        contact,
        budget,
        preferredZone,
        preferredPropertyType,
        preferredRooms);
  }
}
