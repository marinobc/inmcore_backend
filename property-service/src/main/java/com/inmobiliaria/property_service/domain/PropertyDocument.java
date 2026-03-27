package com.inmobiliaria.property_service.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Document(collection = "properties")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PropertyDocument extends BaseDocument {
    @Id
    private String id;
    private String title;
    private String address;
    private Double price;
    private String type;
    private Double m2;
    private Integer rooms;
    private String status;
    private String assignedAgentId;
    private String ownerId;

    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @Builder.Default
    private List<AssignmentHistory> assignmentHistory = new ArrayList<>();

    @Builder.Default
    private List<PriceHistory> priceHistory = new ArrayList<>();

    @Builder.Default
    private Set<String> accessPolicy = new HashSet<>();  // roles o usuarios con permiso de lectura

}