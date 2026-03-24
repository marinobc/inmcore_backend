package com.inmobiliaria.user_service.controller;

import com.inmobiliaria.user_service.dto.request.CreateEmployeeRequest;
import com.inmobiliaria.user_service.dto.request.CreateInterestedClientRequest;
import com.inmobiliaria.user_service.dto.request.CreateOwnerRequest;
import com.inmobiliaria.user_service.dto.request.CreatePersonRequest;
import com.inmobiliaria.user_service.dto.request.UpdatePersonRequest;
import com.inmobiliaria.user_service.dto.response.PersonResponse;
import com.inmobiliaria.user_service.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/persons")
@RequiredArgsConstructor
public class PersonController {

    private final PersonService personService;
    private final AdminProfileService adminProfileService;
    private final EmployeeProfileService employeeProfileService;
    private final OwnerProfileService ownerProfileService;
    private final InterestedClientProfileService interestedClientProfileService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PersonResponse create(@RequestBody @Valid CreatePersonRequest request) {
        return adminProfileService.createAdminProfile(request);
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonResponse createEmployee(@RequestBody @Valid CreateEmployeeRequest request) {
        return employeeProfileService.createEmployeeProfile(request);
    }

    @PostMapping("/owners")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonResponse createOwner(@RequestBody @Valid CreateOwnerRequest request) {
        return ownerProfileService.createOwnerProfile(request);
    }

    @PostMapping("/clients-interested")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonResponse createInterestedClient(@RequestBody @Valid CreateInterestedClientRequest request) {
        return interestedClientProfileService.createInterestedClientProfile(request);
    }

    @GetMapping
    public List<PersonResponse> findAll(@RequestParam(required = false) String type) {
        return personService.findAll(type);
    }

    @GetMapping("/{id}")
    public PersonResponse findById(@PathVariable String id) {
        return personService.findById(id);
    }

    @GetMapping("/by-auth/{authUserId}")
    public PersonResponse findByAuthUserId(@PathVariable String authUserId) {
        return personService.findByAuthUserId(authUserId);
    }

    @PutMapping("/{id}")
    public PersonResponse update(@PathVariable String id, @RequestBody UpdatePersonRequest request) {
        return personService.update(id, request);
    }

    @PutMapping("/by-auth/{authUserId}")
    public PersonResponse updateByAuthUserId(@PathVariable String authUserId, @RequestBody UpdatePersonRequest request) {
        return personService.updateByAuthUserId(authUserId, request);
    }

    @PostMapping("/{id}/assign-role")
    public PersonResponse assignRoles(
            @PathVariable String id,
            @RequestBody List<String> roleIds,
            @RequestParam(defaultValue = "false") boolean isCustom) {
        return personService.assignRoles(id, roleIds, isCustom);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        personService.deleteById(id);
    }

    @DeleteMapping("/by-auth/{authUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteByAuthUserId(@PathVariable String authUserId) {
        personService.deleteByAuthUserId(authUserId);
    }
}