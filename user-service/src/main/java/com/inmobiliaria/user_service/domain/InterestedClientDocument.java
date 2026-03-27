// REEMPLAZAR el archivo completo:
package com.inmobiliaria.user_service.domain;

import lombok.*;
import org.springframework.data.annotation.TypeAlias;
import java.util.List;

@TypeAlias("interested_client")
@Getter
@Setter
@NoArgsConstructor
public class InterestedClientDocument extends PersonDocument {
    private String preferredContactMethod;
    private String budget;
    private List<String> interestedPropertyIds;

    // Campos nuevos de preferencias
    private String preferredZone;
    private String preferredPropertyType;
    private Integer preferredRooms;

    @Builder
    public InterestedClientDocument(String id, String authUserId, String firstName, String lastName,
                                     String fullName, java.time.LocalDate birthDate, String phone,
                                     String email, java.util.List<String> roleIds, boolean customRole,
                                     String preferredContactMethod, String budget,
                                     List<String> interestedPropertyIds,
                                     String preferredZone, String preferredPropertyType,
                                     Integer preferredRooms) {
        super(id, authUserId, firstName, lastName, fullName, birthDate, phone, email,
              PersonType.INTERESTED_CLIENT, roleIds, customRole);
        this.preferredContactMethod = preferredContactMethod;
        this.budget = budget;
        this.interestedPropertyIds = interestedPropertyIds;
        this.preferredZone = preferredZone;
        this.preferredPropertyType = preferredPropertyType;
        this.preferredRooms = preferredRooms;
    }
}