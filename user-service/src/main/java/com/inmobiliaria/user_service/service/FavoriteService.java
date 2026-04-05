package com.inmobiliaria.user_service.service;

import com.inmobiliaria.user_service.domain.FavoriteDocument;
import com.inmobiliaria.user_service.domain.FavoriteHistoryDocument;
import com.inmobiliaria.user_service.repository.FavoriteRepository;
import com.inmobiliaria.user_service.repository.FavoriteHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final FavoriteHistoryRepository favoriteHistoryRepository;


    public void addFavorite(String authUserId, String propertyId) {

        if (propertyId == null || propertyId.isBlank()) {
            throw new IllegalArgumentException("propertyId is required");
        }

        FavoriteDocument fav = favoriteRepository.findByAuthUserIdAndPropertyId(authUserId, propertyId)
                .orElse(null);

        Instant now = Instant.now();

        if (fav != null) {
            if (fav.isActive()) {
                log.info("Favorite already active for user {} property {}", authUserId, propertyId);
                return;
            }
            fav.setActive(true);
            fav.setLastToggledAt(now);
            favoriteRepository.save(fav);
        } else {
            fav = FavoriteDocument.builder()
                    .authUserId(authUserId)
                    .propertyId(propertyId)
                    .createdAt(now)
                    .lastToggledAt(now)
                    .active(true)
                    .build();
            favoriteRepository.save(fav);
        }

        favoriteHistoryRepository.save(FavoriteHistoryDocument.builder()
                .authUserId(authUserId)
                .propertyId(propertyId)
                .action("ADDED")
                .timestamp(now)
                .build());

        log.info("Favorite ADDED for user {} property {}", authUserId, propertyId);
    }

    public void removeFavorite(String authUserId, String propertyId) {
        FavoriteDocument fav = favoriteRepository.findByAuthUserIdAndPropertyId(authUserId, propertyId)
                .orElse(null);

        Instant now = Instant.now();

        if (fav != null && fav.isActive()) {
            fav.setActive(false);
            fav.setLastToggledAt(now);
            favoriteRepository.save(fav);

            favoriteHistoryRepository.save(FavoriteHistoryDocument.builder()
                    .authUserId(authUserId)
                    .propertyId(propertyId)
                    .action("REMOVED")
                    .timestamp(now)
                    .build());

            log.info("Favorite REMOVED for user {} property {}", authUserId, propertyId);
        } else {
            log.info("Favorite not active, nothing to remove for user {} property {}", authUserId, propertyId);
        }
    }

    public List<String> getFavoriteIdsByClient(String authUserId) {
        return favoriteRepository.findByAuthUserId(authUserId).stream()
                .filter(FavoriteDocument::isActive)
                .map(FavoriteDocument::getPropertyId)
                .toList();
    }

    public List<Map<String, Object>> getFavoriteHistory(String authUserId, int limit) {
        return favoriteHistoryRepository
                .findByAuthUserIdOrderByTimestampDesc(authUserId, PageRequest.of(0, Math.min(limit, 100)))
                .stream()
                .map(this::toHistoryMap)
                .toList();
    }

    public List<Map<String, Object>> getPropertyFavoriteHistory(String authUserId, String propertyId) {
        return favoriteHistoryRepository
                .findByAuthUserIdAndPropertyIdOrderByTimestampDesc(authUserId, propertyId)
                .stream()
                .map(this::toHistoryMap)
                .toList();
    }

    private Map<String, Object> toHistoryMap(FavoriteHistoryDocument doc) {
        return Map.of(
                "propertyId", doc.getPropertyId(),
                "action", doc.getAction(),
                "timestamp", doc.getTimestamp().toString()
        );
    }
}