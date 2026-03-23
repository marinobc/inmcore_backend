package com.inmobiliaria.user_service.service;

import com.inmobiliaria.user_service.domain.PersonType;
import com.inmobiliaria.user_service.dto.request.CreateInterestedClientRequest;
import com.inmobiliaria.user_service.dto.request.CreatePersonRequest;
import com.inmobiliaria.user_service.dto.response.PersonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterestedClientProfileService {
    private final PersonService personService;

    public PersonResponse createInterestedClientProfile(CreateInterestedClientRequest request) {
        CreatePersonRequest genericRequest = new CreatePersonRequest(
                request.authUserId(),
                request.firstName(),
                request.lastName(),
                request.birthDate(),
                request.phone(),
                request.email(),
                PersonType.INTERESTED_CLIENT,
                null,               // roleIds
                null,               // department
                null,               // position
                null,               // hireDate
                null,               // taxId
                null,               // address
                null,               // propertyIds
                request.preferredContactMethod(),
                request.budget()
        );
        return personService.create(genericRequest);
    }
}
