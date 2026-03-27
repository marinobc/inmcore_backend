package com.inmobiliaria.identity_service.client;

import com.inmobiliaria.identity_service.client.dto.CreatePersonRequest;
import com.inmobiliaria.identity_service.client.dto.UpdatePersonRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "user-service")
public interface UserServiceClient {

    @PostMapping("/persons")
    Map<String, Object> createPerson(@RequestBody CreatePersonRequest request);

    @PutMapping("/persons/by-auth/{authUserId}")
    Map<String, Object> updatePersonByAuth(@PathVariable("authUserId") String authUserId, @RequestBody UpdatePersonRequest request);

    @GetMapping("/persons/by-auth/{authUserId}")
    Map<String, Object> getPersonByAuthUserId(@PathVariable("authUserId") String authUserId);

    @DeleteMapping("/persons/{id}")
    void deletePerson(@PathVariable("id") String id);

    @DeleteMapping("/persons/by-auth/{authUserId}")
    void deleteByAuthUserId(@PathVariable("authUserId") String authUserId);
}
