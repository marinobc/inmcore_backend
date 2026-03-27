package com.inmobiliaria.access_control_service.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleDocument extends BaseDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    @Indexed(unique = true)
    private String name;

    private String description;
    private RoleType type;
    private Boolean active;
    private List<PermissionEntry> permissions;
    private Integer version;
}