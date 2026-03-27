package com.inmobiliaria.user_service.service;

import com.inmobiliaria.user_service.dto.request.CreatePersonRequest;
import com.inmobiliaria.user_service.dto.response.PersonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminProfileService {
    private final PersonService personService;

    public PersonResponse createAdminProfile(CreatePersonRequest request) {
        return personService.create(request);
    }
}
