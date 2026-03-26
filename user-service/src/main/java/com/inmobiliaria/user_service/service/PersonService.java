package com.inmobiliaria.user_service.service;

import com.inmobiliaria.user_service.client.AccessControlClient;
import com.inmobiliaria.user_service.client.IdentityClient;
import com.inmobiliaria.user_service.domain.*;
import com.inmobiliaria.user_service.dto.request.CreateInterestedClientRequest;
import com.inmobiliaria.user_service.dto.request.CreatePersonRequest;
import com.inmobiliaria.user_service.dto.request.UpdatePersonRequest;
import com.inmobiliaria.user_service.dto.response.PersonResponse;
import com.inmobiliaria.user_service.exception.ResourceAlreadyExistsException;
import com.inmobiliaria.user_service.exception.ResourceNotFoundException;
import com.inmobiliaria.user_service.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonService {

    private final PersonRepository personRepository;
    private final IdentityClient identityClient;
    private final AccessControlClient accessControlClient;

    public PersonResponse create(CreatePersonRequest request) {
        log.info("Creating person profile for authUserId: {}", request.authUserId());

        if (personRepository.existsByAuthUserId(request.authUserId())) {
            throw new ResourceAlreadyExistsException("Profile already exists for authUserId: " + request.authUserId());
        }

        if (request.roleIds() != null && !request.roleIds().isEmpty()) {
            boolean validRoles = accessControlClient.validateRoleIds(request.roleIds());
            if (!validRoles) {
                throw new ResourceNotFoundException("One or more role IDs are invalid");
            }
        }

        PersonDocument document;
        switch (request.personType()) {
            case ADMIN -> document = AdminDocument.builder()
                    .authUserId(request.authUserId())
                    .firstName(request.firstName())
                    .lastName(request.lastName())
                    .fullName(request.firstName() + " " + request.lastName())
                    .birthDate(request.birthDate())
                    .phone(request.phone())
                    .email(request.email())
                    .roleIds(request.roleIds())
                    .build();
            case EMPLOYEE -> document = EmployeeDocument.builder()
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
            case OWNER -> document = OwnerDocument.builder()
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
            case INTERESTED_CLIENT -> document = InterestedClientDocument.builder()
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
                    .build();
            default -> throw new IllegalArgumentException("Unsupported person type: " + request.personType());
        }

        document.setCreatedAt(Instant.now());
        document.setUpdatedAt(Instant.now());
        document.setCreatedBy("system");

        PersonDocument saved = personRepository.save(document);

        // --- ASIGNAR CLIENTE AL AGENTE SI SE PROPORCIONÓ assignedAgentId ---
        if (request.assignedAgentId() != null && request.personType() == PersonType.INTERESTED_CLIENT) {
            Optional<EmployeeDocument> agentOpt = personRepository.findEmployeeByAuthUserId(request.assignedAgentId());
            if (agentOpt.isEmpty()) {
                log.warn("Agent EmployeeDocument not found for authUserId: {}. Client created without assignment.", request.assignedAgentId());
            } else {
                EmployeeDocument agent = agentOpt.get();
                if (agent.getAssignedClientIds() == null) {
                    agent.setAssignedClientIds(new ArrayList<>());
                }
                agent.getAssignedClientIds().add(saved.getId());
                personRepository.save(agent);
                log.info("Client {} assigned to agent {}", saved.getId(), agent.getId());
            }
        }

        return mapToResponse(saved);
    }

    public List<PersonResponse> findAll(String type) {
        if (type != null) {
            return personRepository.findAll().stream()
                    .filter(p -> p.getPersonType().name().equalsIgnoreCase(type))
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }
        return personRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public PersonResponse findById(String id) {
        return personRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found with id: " + id));
    }

    public PersonResponse findByAuthUserId(String authUserId) {
        return personRepository.findByAuthUserId(authUserId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found with authUserId: " + authUserId));
    }

    public PersonResponse update(String id, UpdatePersonRequest request) {
        PersonDocument person = personRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found with id: " + id));

        List<AuditEntry.FieldChange> changes = new ArrayList<>();

        if (request.firstName() != null && !request.firstName().equals(person.getFirstName())) {
            changes.add(AuditEntry.FieldChange.builder()
                    .field("firstName").oldValue(person.getFirstName()).newValue(request.firstName()).build());
            person.setFirstName(request.firstName());
        }
        if (request.lastName() != null && !request.lastName().equals(person.getLastName())) {
            changes.add(AuditEntry.FieldChange.builder()
                    .field("lastName").oldValue(person.getLastName()).newValue(request.lastName()).build());
            person.setLastName(request.lastName());
        }
        if (person.getFirstName() != null && person.getLastName() != null) {
            person.setFullName(person.getFirstName() + " " + person.getLastName());
        }
        if (request.birthDate() != null && !request.birthDate().equals(person.getBirthDate())) {
            changes.add(AuditEntry.FieldChange.builder()
                    .field("birthDate")
                    .oldValue(person.getBirthDate() != null ? person.getBirthDate().toString() : null)
                    .newValue(request.birthDate().toString()).build());
            person.setBirthDate(request.birthDate());
        }
        if (request.phone() != null && !request.phone().equals(person.getPhone())) {
            changes.add(AuditEntry.FieldChange.builder()
                    .field("phone").oldValue(person.getPhone()).newValue(request.phone()).build());
            person.setPhone(request.phone());
        }

        if (person instanceof EmployeeDocument emp) {
            if (request.department() != null && !request.department().equals(emp.getDepartment())) {
                changes.add(AuditEntry.FieldChange.builder()
                        .field("department").oldValue(emp.getDepartment()).newValue(request.department()).build());
                emp.setDepartment(request.department());
            }
            if (request.position() != null && !request.position().equals(emp.getPosition())) {
                changes.add(AuditEntry.FieldChange.builder()
                        .field("position").oldValue(emp.getPosition()).newValue(request.position()).build());
                emp.setPosition(request.position());
            }
            if (request.hireDate() != null && !request.hireDate().equals(emp.getHireDate())) {
                changes.add(AuditEntry.FieldChange.builder()
                        .field("hireDate")
                        .oldValue(emp.getHireDate() != null ? emp.getHireDate().toString() : null)
                        .newValue(request.hireDate().toString()).build());
                emp.setHireDate(request.hireDate());
            }
        } else if (person instanceof OwnerDocument owner) {
            if (request.taxId() != null && !request.taxId().equals(owner.getTaxId())) {
                changes.add(AuditEntry.FieldChange.builder()
                        .field("taxId").oldValue(owner.getTaxId()).newValue(request.taxId()).build());
                owner.setTaxId(request.taxId());
            }
        } else if (person instanceof InterestedClientDocument client) {
            if (request.preferredContactMethod() != null && !request.preferredContactMethod().equals(client.getPreferredContactMethod())) {
                changes.add(AuditEntry.FieldChange.builder()
                        .field("preferredContactMethod")
                        .oldValue(client.getPreferredContactMethod())
                        .newValue(request.preferredContactMethod()).build());
                client.setPreferredContactMethod(request.preferredContactMethod());
            }
            if (request.budget() != null && !request.budget().equals(client.getBudget())) {
                changes.add(AuditEntry.FieldChange.builder()
                        .field("budget").oldValue(client.getBudget()).newValue(request.budget()).build());
                client.setBudget(request.budget());
            }
        }

        if (!changes.isEmpty()) {
            // Obtener el editor desde el header X-Auth-User-Id
            String changedBy = getCurrentUserId();

            AuditEntry entry = AuditEntry.builder()
                    .changedAt(Instant.now())
                    .changedBy(changedBy)
                    .changes(changes)
                    .build();

            if (person.getAuditLog() == null) {
                person.setAuditLog(new ArrayList<>());
            }
            person.getAuditLog().add(entry);

            // Persistencia en archivo via Slf4j (va a logs/user-service-audit.log)
            changes.forEach(c -> log.info("AUDIT | person={} | changedBy={} | field={} | old={} | new={}",
                    person.getId(), changedBy, c.getField(), c.getOldValue(), c.getNewValue()));
        }

        person.setUpdatedAt(Instant.now());
        return mapToResponse(personRepository.save(person));
    }

    public PersonResponse updateByAuthUserId(String authUserId, UpdatePersonRequest request) {
        PersonDocument person = personRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found with authUserId: " + authUserId));
        
        return update(person.getId(), request);
    }

    public PersonResponse assignRoles(String id, List<String> roleIds, boolean isCustom) {
        PersonDocument person = personRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found with id: " + id));

        if (roleIds != null && !roleIds.isEmpty()) {
            boolean validRoles = accessControlClient.validateRoleIds(roleIds);
            if (!validRoles) {
                throw new ResourceNotFoundException("One or more role IDs are invalid");
            }
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

    // Obtener clientes asignados al agente
    public List<PersonResponse> getClientsForAgent(String agentId) {
        EmployeeDocument agent = personRepository.findEmployeeByAuthUserId(agentId)
            .orElseThrow(() -> new ResourceNotFoundException("Agent not found with id: " + agentId));

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

    // Crear un cliente y asignarlo al agente
    public PersonResponse createClientForAgent(String agentId, CreateInterestedClientRequest request) {
        // Validar que el agente exista
        EmployeeDocument agent = personRepository.findEmployeeByAuthUserId(agentId)
            .orElseThrow(() -> new ResourceNotFoundException("Agent not found with id: " + agentId));

        // Construir el documento del cliente
        InterestedClientDocument client = InterestedClientDocument.builder()
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

        // Asignar al agente
        if (agent.getAssignedClientIds() == null) {
            agent.setAssignedClientIds(new ArrayList<>());
        }
        agent.getAssignedClientIds().add(client.getId());
        personRepository.save(agent);

        return mapToResponse(client);
    }

    // Actualizar un cliente solo si está asignado al agente
    public PersonResponse updateClientForAgent(String agentId, String clientId, UpdatePersonRequest request) {
        EmployeeDocument agent = personRepository.findEmployeeByAuthUserId(agentId)
            .orElseThrow(() -> new ResourceNotFoundException("Agent not found with id: " + agentId));

        if (agent.getAssignedClientIds() == null || !agent.getAssignedClientIds().contains(clientId)) {
            throw new ResourceNotFoundException("Client not found or not assigned to you");
        }

        PersonDocument client = personRepository.findById(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

        // Actualizar campos permitidos
        if (request.firstName() != null) client.setFirstName(request.firstName());
        if (request.lastName() != null) client.setLastName(request.lastName());
        if (client.getFirstName() != null && client.getLastName() != null) {
            client.setFullName(client.getFirstName() + " " + client.getLastName());
        }
        if (request.birthDate() != null) client.setBirthDate(request.birthDate());
        if (request.phone() != null) client.setPhone(request.phone());

        if (client instanceof InterestedClientDocument interested) {
            if (request.preferredContactMethod() != null) interested.setPreferredContactMethod(request.preferredContactMethod());
            if (request.budget() != null) interested.setBudget(request.budget());
        }

        client.setUpdatedAt(Instant.now());
        return mapToResponse(personRepository.save(client));
    }

    private String getCurrentUserId() {
        try {
            jakarta.servlet.http.HttpServletRequest request =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes())
                .getRequest();
            String userId = request.getHeader("X-Auth-User-Id");
            return userId != null ? userId : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private PersonResponse mapToResponse(PersonDocument document) {
        String dept = null, pos = null, tax = null, address = null,
            contact = null, budget = null;
        List<String> propertyIds = null;
        LocalDate hire = null;

        if (document instanceof EmployeeDocument emp) {
            dept = emp.getDepartment();
            pos  = emp.getPosition();
            hire = emp.getHireDate();
        } else if (document instanceof OwnerDocument owner) {
            tax        = owner.getTaxId();
            address    = owner.getAddress();        // NUEVO
            propertyIds = owner.getPropertyIds();   // NUEVO
        } else if (document instanceof InterestedClientDocument client) {
            contact = client.getPreferredContactMethod();
            budget  = client.getBudget();
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
                dept, pos, hire,
                tax, address, propertyIds,  // NUEVO
                contact, budget
        );
    }
}