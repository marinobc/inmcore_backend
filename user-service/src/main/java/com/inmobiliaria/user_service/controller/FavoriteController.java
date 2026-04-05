package com.inmobiliaria.user_service.controller;

import com.inmobiliaria.user_service.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favoritos")
@RequiredArgsConstructor
public class FavoriteController {
    private final FavoriteService favoriteService;

    @PostMapping
    public void add(@RequestBody Map<String, String> body, 
                    @RequestHeader(value = "X-Auth-User-Id", required = false) String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Missing X-Auth-User-Id header");
        }
        favoriteService.addFavorite(userId, body.get("propertyId"));
    }

    @DeleteMapping("/{propertyId}")
    public void remove(@PathVariable String propertyId, 
                       @RequestHeader(value = "X-Auth-User-Id", required = false) String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Missing X-Auth-User-Id header");
        }
        favoriteService.removeFavorite(userId, propertyId);
    }

    @GetMapping
    public List<String> list(@RequestHeader(value = "X-Auth-User-Id", required = false) String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Missing X-Auth-User-Id header");
        }
        return favoriteService.getFavoriteIdsByClient(userId);
    }

    @GetMapping("/history")
    public List<Map<String, Object>> history(
            @RequestHeader(value = "X-Auth-User-Id", required = false) String userId,
            @RequestParam(defaultValue = "20") int limit) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Missing X-Auth-User-Id header");
        }
        return favoriteService.getFavoriteHistory(userId, limit);
    }

    @GetMapping("/history/{propertyId}")
    public List<Map<String, Object>> propertyHistory(
            @PathVariable String propertyId,
            @RequestHeader(value = "X-Auth-User-Id", required = false) String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Missing X-Auth-User-Id header");
        }
        return favoriteService.getPropertyFavoriteHistory(userId, propertyId);
    }
}