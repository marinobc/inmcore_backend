package com.inmobiliaria.property_service.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Document(collection = "properties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyDocument extends BaseDocument {
    @Id
    private String id;
    private String title;
    private String address;
    private Double price;
    private String type;
    private OperationType operationType;
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
    private Set<String> accessPolicy = new HashSet<>();

    @Builder.Default
    private List<DocumentMetadata> documents = new ArrayList<>();

    @Builder.Default
    private List<ImageMetadata> images = new ArrayList<>();

    @Builder.Default
    private boolean deleted = false;

    // Helper methods
    public void addImageUrl(String imageUrl) {
        if (this.imageUrls == null) {
            this.imageUrls = new ArrayList<>();
        }
        if (!this.imageUrls.contains(imageUrl)) {
            this.imageUrls.add(imageUrl);
        }
    }

    public void removeImageUrl(String imageUrl) {
        if (this.imageUrls != null) {
            this.imageUrls.removeIf(url -> url.equals(imageUrl) || url.contains(imageUrl));
        }
    }
}