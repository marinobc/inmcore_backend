package com.inmobiliaria.user_service.domain;

import lombok.*;
import org.springframework.data.annotation.TypeAlias;
import java.time.LocalDate;

@TypeAlias("employee")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeDocument extends PersonDocument {
    private String department;
    private String position;
    private LocalDate hireDate;

    // NUEVO: Lista de IDs de clientes asignados
    private java.util.List<String> assignedClientIds = new java.util.ArrayList<>();

    @Builder
    public EmployeeDocument(String id, String authUserId, String firstName, String lastName, String fullName,
                            java.time.LocalDate birthDate, String phone, String email,
                            java.util.List<String> roleIds, boolean customRole,
                            String department, String position, LocalDate hireDate,
                            java.util.List<String> assignedClientIds) {
        super(id, authUserId, firstName, lastName, fullName, birthDate, phone, email, PersonType.EMPLOYEE, roleIds, customRole);
        this.department = department;
        this.position = position;
        this.hireDate = hireDate;
        this.assignedClientIds = assignedClientIds != null ? assignedClientIds : new java.util.ArrayList<>();
    }
}