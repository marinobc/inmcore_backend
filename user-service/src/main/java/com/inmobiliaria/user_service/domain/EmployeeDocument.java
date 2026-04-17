package com.inmobiliaria.user_service.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.TypeAlias;

import lombok.*;

@TypeAlias("employee")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeDocument extends PersonDocument {
  private String department;
  private String position;
  private LocalDate hireDate;

  // NUEVO: Lista de IDs de clientes asignados
  private List<String> assignedClientIds = new ArrayList<>();

  // NUEVO: Lista de IDs de propietarios asignados
  private List<String> assignedOwnerIds = new ArrayList<>();

  @Builder
  public EmployeeDocument(
      String id,
      String authUserId,
      String firstName,
      String lastName,
      String fullName,
      java.time.LocalDate birthDate,
      String phone,
      String email,
      List<String> roleIds,
      boolean customRole,
      String department,
      String position,
      LocalDate hireDate,
      List<String> assignedClientIds,
      List<String> assignedOwnerIds) {
    super(
        id,
        authUserId,
        firstName,
        lastName,
        fullName,
        birthDate,
        phone,
        email,
        PersonType.EMPLOYEE,
        roleIds,
        customRole);
    this.department = department;
    this.position = position;
    this.hireDate = hireDate;
    this.assignedClientIds = assignedClientIds != null ? assignedClientIds : new ArrayList<>();
    this.assignedOwnerIds = assignedOwnerIds != null ? assignedOwnerIds : new ArrayList<>();
  }
}
