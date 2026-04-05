package com.inmobiliaria.user_service.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "favorites")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@CompoundIndex(name = "user_property_idx", def = "{'authUserId': 1, 'propertyId': 1}", unique = true)
public class FavoriteDocument {
    @Id
    private String id;
    
    @Indexed
    private String authUserId;
    
    @Indexed
    private String propertyId;
    
    private Instant createdAt;

    // Track toggle history
    private Instant lastToggledAt;
    private boolean active;
}