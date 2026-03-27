package com.inmobiliaria.user_service.domain;

import lombok.*;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("admin")
@Getter
@Setter
@NoArgsConstructor
public class AdminDocument extends PersonDocument {
    @Builder
    public AdminDocument(String id, String authUserId, String firstName, String lastName, String fullName, 
                         java.time.LocalDate birthDate, String phone, String email, 
                         java.util.List<String> roleIds, boolean customRole) {
        super(id, authUserId, firstName, lastName, fullName, birthDate, phone, email, PersonType.ADMIN, roleIds, customRole);
    }
}
