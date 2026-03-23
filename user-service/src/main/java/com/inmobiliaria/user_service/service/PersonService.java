package com.inmobiliaria.user_service.service;

import com.inmobiliaria.user_service.client.AccessControlClient;
import com.inmobiliaria.user_service.client.IdentityClient;
import com.inmobiliaria.user_service.domain.*;
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
import java.util.List;
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

        try {
            identityClient.findById(request.authUserId());
        } catch (Exception e) {
            log.error("Failed to verify authUserId: {}", request.authUserId(), e);
            throw new ResourceNotFoundException("Identity user not found: " + request.authUserId());
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

        return mapToResponse(personRepository.save(document));
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

        if (request.firstName() != null) person.setFirstName(request.firstName());
        if (request.lastName() != null) person.setLastName(request.lastName());
        if (person.getFirstName() != null && person.getLastName() != null) {
            person.setFullName(person.getFirstName() + " " + person.getLastName());
        }
        if (request.birthDate() != null) person.setBirthDate(request.birthDate());
        if (request.phone() != null) person.setPhone(request.phone());

        if (person instanceof EmployeeDocument emp) {
            if (request.department() != null) emp.setDepartment(request.department());
            if (request.position() != null) emp.setPosition(request.position());
            if (request.hireDate() != null) emp.setHireDate(request.hireDate());
        } else if (person instanceof OwnerDocument owner) {
            if (request.taxId() != null) owner.setTaxId(request.taxId());
        } else if (person instanceof InterestedClientDocument client) {
            if (request.preferredContactMethod() != null) client.setPreferredContactMethod(request.preferredContactMethod());
            if (request.budget() != null) client.setBudget(request.budget());
        }

        person.setUpdatedAt(Instant.now());
        return mapToResponse(personRepository.save(person));
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

    private PersonResponse mapToResponse(PersonDocument document) {
        String dept = null, pos = null, tax = null, address = null,
            contact = null, budget = null;
        List<String> propertyIds = null;
        Instant hire = null;

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