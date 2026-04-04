package com.inmobiliaria.property_service.controller;

import com.inmobiliaria.property_service.dto.request.ConfirmImageUploadRequest;
import com.inmobiliaria.property_service.dto.request.GenerateImageUploadUrlRequest;
import com.inmobiliaria.property_service.dto.response.ImageResponse;
import com.inmobiliaria.property_service.dto.response.PropertyResponse;
import com.inmobiliaria.property_service.service.ImageService;
import com.inmobiliaria.property_service.service.PropertyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/properties/{propertyId}/images") // Base path for images sub-resource
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;
    private final PropertyService propertyService;

    /**
     * Step 1: Get Presigned URL for uploading
     */
    @PostMapping("/upload-url")
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public Map<String, String> generateUploadUrl(
            @PathVariable String propertyId,
            @Valid @RequestBody GenerateImageUploadUrlRequest request) {
        request.setPropertyId(propertyId);
        return imageService.generatePresignedUploadUrl(request);
    }

    /**
     * Step 2: Confirm upload and attach metadata to Property
     */
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public PropertyResponse confirmUpload(
            @PathVariable String propertyId,
            @Valid @RequestBody ConfirmImageUploadRequest request) {
        
        log.info("Confirming image for property: {}, objectKey: {}", propertyId, request.getObjectKey());
        
        var property = imageService.confirmImageUpload(
            propertyId, 
            request.getObjectKey(), 
            request.getOriginalFileName(),
            request.getFileSize(),
            request.getMimeType(),
            request.getIsPrimary() != null ? request.getIsPrimary() : false
        );
        
        return propertyService.mapToResponse(property);
    }

    /**
     * List all images for a property
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ImageResponse> getImages(@PathVariable String propertyId) {
        return imageService.getPropertyImages(propertyId);
    }

    /**
     * Set primary image
     */
    @PutMapping("/{imageId}/primary")
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public ImageResponse setPrimaryImage(@PathVariable String propertyId, @PathVariable String imageId) {
        return imageService.setPrimaryImage(propertyId, imageId);
    }

    /**
     * Reorder images
     */
    @PostMapping("/reorder")
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public List<ImageResponse> reorderImages(
            @PathVariable String propertyId,
            @RequestBody List<String> orderedImageIds) {
        return imageService.reorderImages(propertyId, orderedImageIds);
    }

    /**
     * Delete image
     */
    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public void deleteImage(@PathVariable String propertyId, @PathVariable String imageId) {
        imageService.deleteImage(propertyId, imageId);
    }
}