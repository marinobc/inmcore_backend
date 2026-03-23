package com.inmobiliaria.user_service.service;

import com.inmobiliaria.user_service.domain.PersonType;
import com.inmobiliaria.user_service.dto.request.CreateOwnerRequest;
import com.inmobiliaria.user_service.dto.request.CreatePersonRequest;
import com.inmobiliaria.user_service.dto.response.PersonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OwnerProfileService {
    private final PersonService personService;

    public PersonResponse createOwnerProfile(CreateOwnerRequest request) {
        CreatePersonRequest genericRequest = new CreatePersonRequest(
                request.authUserId(),
                request.firstName(),
                request.lastName(),
                request.birthDate(),
                request.phone(),
                request.email(),
                PersonType.OWNER,
                null,
                null, null, null,   // employee fields
                request.taxId(),
                request.address(),
                request.propertyIds(),
                null, null              // client fields
        );
        return personService.create(genericRequest);
    }
}