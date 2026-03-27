package com.inmobiliaria.access_control_service.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "permissions_catalog")
@CompoundIndex(
        name = "uk_permission_catalog_resource_action_scope",
        def = "{'resource': 1, 'action': 1, 'scope': 1}",
        unique = true
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionCatalogDocument extends BaseDocument {

    @Id
    private String id;

    private String resource;
    private String action;
    private ScopeType scope;
    private String description;
    private Boolean active;
}