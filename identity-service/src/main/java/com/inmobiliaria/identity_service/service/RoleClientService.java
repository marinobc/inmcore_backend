package com.inmobiliaria.identity_service.service;

import com.mongodb.client.MongoClient;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class RoleClientService {

    private final MongoClient mongoClient;

    @Value("${app.mongo.database}")
    private String databaseName;

    public void validateRoleIdsExist(List<String> roleIds) {
        long count = mongoClient.getDatabase(databaseName)
                .getCollection("roles")
                .countDocuments(new Document("_id", new Document("$in", roleIds))
                        .append("active", true));

        if (count != roleIds.size()) {
            throw new IllegalArgumentException("One or more roleIds do not exist or are inactive");
        }
    }

    /**
     * Converts a list of role IDs (e.g. "rol_admin") into their corresponding
     * role codes (e.g. "ADMIN") by querying the roles collection.
     */
    public List<String> resolveRoleCodes(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return StreamSupport.stream(
                        mongoClient.getDatabase(databaseName)
                                .getCollection("roles")
                                .find(new Document("_id", new Document("$in", roleIds)))
                                .spliterator(), false)
                .map(doc -> doc.getString("code"))
                .collect(Collectors.toList());
    }
}