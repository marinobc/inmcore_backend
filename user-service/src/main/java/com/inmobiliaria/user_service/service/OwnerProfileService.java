package com.inmobiliaria.user_service.service;

import com.inmobiliaria.user_service.domain.PersonType;
import com.inmobiliaria.user_service.dto.request.CreateOwnerRequest;
import com.inmobiliaria.user_service.dto.request.CreatePersonRequest;
import com.inmobiliaria.user_service.dto.response.PersonResponse;
import com.inmobiliaria.user_service.exception.ResourceAlreadyExistsException;
import com.inmobiliaria.user_service.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerProfileService {

    private final PersonService personService;
    private final PersonRepository personRepository;

    public PersonResponse createOwnerProfile(CreateOwnerRequest request) {
        log.info("Creating owner profile for taxId: {}", request.taxId());

        // Verificar CI duplicado
        personRepository.findOwnerByTaxId(request.taxId().trim())
                .ifPresent(existing -> {
                    throw new ResourceAlreadyExistsException(
                            "An owner with CI " + request.taxId() + " already exists"
                    );
                });

        // Verificar email duplicado
        if (personRepository.existsByEmail(request.email().trim().toLowerCase())) {
            throw new ResourceAlreadyExistsException(
                    "A person with email " + request.email() + " already exists"
            );
        }

        // Validar que la fecha de nacimiento sea razonable
        LocalDate minDate = LocalDate.now().minusYears(120);
        LocalDate maxDate = LocalDate.now().minusYears(18);
        if (request.birthDate().isBefore(minDate) || request.birthDate().isAfter(maxDate)) {
            throw new IllegalArgumentException(
                    "Owner must be at least 18 years old and birth date must be valid"
            );
        }

        CreatePersonRequest genericRequest = new CreatePersonRequest(
                request.authUserId(),
                request.firstName().trim(),
                request.lastName().trim(),
                request.birthDate(),
                request.phone().trim(),
                request.email().trim().toLowerCase(),
                PersonType.OWNER,
                null,               // roleIds
                null,               // department
                null,               // position
                null,               // hireDate
                request.taxId().trim(),
                request.address().trim(),
                request.propertyIds(),
                null,               // preferredContactMethod
                null,                       //budget
                null,                // assignedAgentId
                null,                // preferredZone
                null,                // preferredPropertyType
                null                 // preferredRooms
        );

        return personService.create(genericRequest);
    }
}