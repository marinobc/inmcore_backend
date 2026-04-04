package com.inmobiliaria.property_service.service;

import com.inmobiliaria.property_service.domain.ImageMetadata;
import com.inmobiliaria.property_service.domain.PropertyDocument;
import com.inmobiliaria.property_service.dto.request.GenerateImageUploadUrlRequest;
import com.inmobiliaria.property_service.dto.response.ImageResponse;
import com.inmobiliaria.property_service.exception.AccessDeniedException;
import com.inmobiliaria.property_service.exception.ResourceNotFoundException;
import com.inmobiliaria.property_service.exception.ValidationException;
import com.inmobiliaria.property_service.repository.PropertyRepository;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private final MinioClient minioClient;
    private final PropertyRepository propertyRepository;

    @Value("${minio.presigned.expiry-minutes:15}")
    private int presignedExpiryMinutes;

    @Value("${minio.images.bucket:property-images}")
    private String imagesBucket;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/heic",
            "image/heif"
    );
    
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_IMAGES_PER_PROPERTY = 20;

    public Map<String, String> generatePresignedUploadUrl(GenerateImageUploadUrlRequest request) {
        if (!isValidImageType(request.getMimeType(), request.getFileName())) {
            throw new ValidationException(
                "Invalid image type. Only JPG, PNG, WebP, and HEIC are allowed."
            );
        }
        
        if (request.getFileSize() > MAX_IMAGE_SIZE) {
            throw new ValidationException(
                String.format("Image size exceeds limit. Maximum: %d MB",
                    MAX_IMAGE_SIZE / (1024 * 1024))
            );
        }
        
        PropertyDocument property = propertyRepository.findById(request.getPropertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + request.getPropertyId()));
        
        if (property.getImages() != null && property.getImages().size() >= MAX_IMAGES_PER_PROPERTY) {
            throw new ValidationException(
                String.format("Maximum %d images per property reached", MAX_IMAGES_PER_PROPERTY)
            );
        }
        
        String currentUserId = getCurrentUserId();
        List<String> roles = getCurrentUserRoles();
        boolean isAdmin = roles.contains("ROLE_ADMIN");
        boolean isAssignedAgent = property.getAssignedAgentId() != null && 
                                  property.getAssignedAgentId().equals(currentUserId);
        
        if (!isAdmin && !isAssignedAgent) {
            throw new AccessDeniedException("You don't have permission to upload images for this property");
        }
        
        ensureImagesBucketExists();
        
        String objectKey = buildImageObjectKey(request.getPropertyId(), request.getFileName());
        
        try {
            String uploadUrl = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(imagesBucket)
                    .object(objectKey)
                    .expiry(presignedExpiryMinutes, TimeUnit.MINUTES)
                    .build()
            );
            
            return Map.of(
                "uploadUrl", uploadUrl,
                "objectKey", objectKey,
                "publicUrl", getPublicUrl(objectKey),
                "expiresInSeconds", String.valueOf(presignedExpiryMinutes * 60)
            );
        } catch (Exception e) {
            log.error("Error generating presigned URL: {}", e.getMessage());
            throw new RuntimeException("Failed to generate upload URL", e);
        }
    }

    public Map<String, String> generatePresignedUploadUrl(String propertyId, String fileName) {
        GenerateImageUploadUrlRequest request = new GenerateImageUploadUrlRequest();
        request.setPropertyId(propertyId);
        request.setFileName(fileName);
        request.setFileSize(0L);
        return generatePresignedUploadUrl(request);
    }

    public String uploadImageDirectly(String propertyId, MultipartFile file) {
        try {
            ensureImagesBucketExists();
            
            String originalFilename = file.getOriginalFilename();
            String safeFileName = originalFilename != null ? originalFilename : "image.jpg";
            String objectKey = buildImageObjectKey(propertyId, safeFileName);
            
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                    io.minio.PutObjectArgs.builder()
                            .bucket(imagesBucket)
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
                );
            }
            
            String publicUrl = getPublicUrl(objectKey);
            log.info("Image uploaded directly for property {}: {}", propertyId, publicUrl);
            
            return publicUrl;
        } catch (Exception e) {
            log.error("Error uploading image for property {}: {}", propertyId, e.getMessage());
            throw new RuntimeException("Failed to upload image", e);
        }
    }

    public PropertyDocument confirmImageUpload(String propertyId, String objectKey, 
                                                String originalFileName, Long fileSize, 
                                                String mimeType, Boolean isPrimary) {
        PropertyDocument property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));
        
        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(imagesBucket)
                    .object(objectKey)
                    .build()
            );
        } catch (Exception e) {
            log.error("Image not found in MinIO: {}", objectKey);
            throw new ValidationException("Image upload not confirmed. Please try uploading again.");
        }
        
        String currentUserId = getCurrentUserId();
        String currentUserName = getCurrentUserName();
        
        int nextOrder = property.getImages() != null ? property.getImages().size() : 0;
        
        if (Boolean.TRUE.equals(isPrimary)) {
            if (property.getImages() != null) {
                property.getImages().forEach(img -> img.setIsPrimary(false));
            }
        } else if (property.getImages() == null || property.getImages().isEmpty()) {
            isPrimary = true;
        }
        
        ImageMetadata image = ImageMetadata.builder()
                .id(UUID.randomUUID().toString())
                .originalFileName(originalFileName)
                .objectKey(objectKey)
                .publicUrl(getPublicUrl(objectKey))
                .fileSize(fileSize)
                .mimeType(mimeType)
                .isPrimary(isPrimary != null ? isPrimary : false)
                .displayOrder(nextOrder)
                .uploadedAt(Instant.now())
                .uploadedBy(currentUserId)
                .uploadedByName(currentUserName)
                .status(ImageMetadata.ImageStatus.ACTIVE)
                .accessPolicy(new HashSet<>())
                .build();
        
        if (property.getImages() == null) {
            property.setImages(new ArrayList<>());
        }
        property.getImages().add(image);
        property.setUpdatedAt(Instant.now());
        
        return propertyRepository.save(property);
    }

    public List<ImageResponse> getPropertyImages(String propertyId) {
        PropertyDocument property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));
        
        checkImageAccessPermission(property, null);
        
        List<ImageResponse> responses = new ArrayList<>();
        if (property.getImages() != null) {
            for (ImageMetadata image : property.getImages()) {
                if (image.getStatus() == ImageMetadata.ImageStatus.ACTIVE) {
                    String tempUrl = generateTemporaryImageUrl(image);
                    responses.add(toImageResponse(image, tempUrl));
                }
            }
        }
        
        responses.sort((a, b) -> {
            if (a.getIsPrimary() && !b.getIsPrimary()) return -1;
            if (!a.getIsPrimary() && b.getIsPrimary()) return 1;
            return Integer.compare(a.getDisplayOrder(), b.getDisplayOrder());
        });
        
        return responses;
    }

    public ImageResponse setPrimaryImage(String propertyId, String imageId) {
        PropertyDocument property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));
        
        if (property.getImages() == null) {
            throw new ResourceNotFoundException("No images found for this property");
        }
        
        ImageMetadata targetImage = null;
        for (ImageMetadata img : property.getImages()) {
            if (img.getId().equals(imageId)) {
                targetImage = img;
                img.setIsPrimary(true);
            } else {
                img.setIsPrimary(false);
            }
        }
        
        if (targetImage == null) {
            throw new ResourceNotFoundException("Image not found: " + imageId);
        }
        
        property.setUpdatedAt(Instant.now());
        propertyRepository.save(property);
        
        String tempUrl = generateTemporaryImageUrl(targetImage);
        return toImageResponse(targetImage, tempUrl);
    }

    public List<ImageResponse> reorderImages(String propertyId, List<String> orderedImageIds) {
        PropertyDocument property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));
        
        if (property.getImages() == null || property.getImages().isEmpty()) {
            throw new ValidationException("No images to reorder");
        }
        
        Map<String, ImageMetadata> imageMap = property.getImages().stream()
                .collect(Collectors.toMap(ImageMetadata::getId, img -> img));
        
        List<ImageMetadata> reordered = new ArrayList<>();
        for (int i = 0; i < orderedImageIds.size(); i++) {
            String id = orderedImageIds.get(i);
            ImageMetadata img = imageMap.get(id);
            if (img != null) {
                img.setDisplayOrder(i);
                reordered.add(img);
                imageMap.remove(id);
            }
        }
        
        reordered.addAll(imageMap.values());
        property.setImages(reordered);
        property.setUpdatedAt(Instant.now());
        propertyRepository.save(property);
        
        return getPropertyImages(propertyId);
    }

    public void deleteImage(String propertyId, String imageId) {
        PropertyDocument property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));
        
        if (property.getImages() == null) {
            throw new ResourceNotFoundException("Image not found");
        }
        
        ImageMetadata toDelete = property.getImages().stream()
                .filter(i -> i.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));
        
        try {
            minioClient.removeObject(
                io.minio.RemoveObjectArgs.builder()
                    .bucket(imagesBucket)
                    .object(toDelete.getObjectKey())
                    .build()
            );
            log.info("Deleted image file from MinIO: {}", toDelete.getObjectKey());
        } catch (Exception e) {
            log.error("Failed to delete image from MinIO: {}", e.getMessage());
        }
        
        property.getImages().removeIf(i -> i.getId().equals(imageId));
        
        if (toDelete.getIsPrimary() && property.getImages() != null && !property.getImages().isEmpty()) {
            property.getImages().get(0).setIsPrimary(true);
        }
        
        for (int i = 0; i < property.getImages().size(); i++) {
            property.getImages().get(i).setDisplayOrder(i);
        }
        
        property.setUpdatedAt(Instant.now());
        propertyRepository.save(property);
        
        log.info("Deleted image: {} from property {}", imageId, propertyId);
    }

    public void deleteAllImagesForProperty(String propertyId) {
        PropertyDocument property = propertyRepository.findById(propertyId).orElse(null);
        
        if (property != null && property.getImages() != null) {
            for (ImageMetadata image : property.getImages()) {
                try {
                    minioClient.removeObject(
                        io.minio.RemoveObjectArgs.builder()
                            .bucket(imagesBucket)
                            .object(image.getObjectKey())
                            .build()
                    );
                } catch (Exception e) {
                    log.error("Error deleting image {}: {}", image.getObjectKey(), e.getMessage());
                }
            }
            log.info("Deleted {} images for property {}", property.getImages().size(), propertyId);
        }
    }

    public PropertyDocument confirmImageUpload(String propertyId, String objectKey) {
        return confirmImageUpload(propertyId, objectKey, null, null, null, null);
    }

    public String generateTemporaryImageUrl(ImageMetadata image) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(imagesBucket)
                    .object(image.getObjectKey())
                    .expiry(presignedExpiryMinutes, TimeUnit.MINUTES)
                    .build()
            );
        } catch (Exception e) {
            log.error("Error generating image URL: {}", e.getMessage());
            return image.getPublicUrl();
        }
    }

    private void checkImageAccessPermission(PropertyDocument property, ImageMetadata image) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        
        String userId = getCurrentUserId();
        Set<String> roles = getCurrentUserRolesSet();
        
        if (roles.contains("ROLE_ADMIN")) {
            return;
        }
        
        boolean isAssignedAgent = property.getAssignedAgentId() != null && 
                                  property.getAssignedAgentId().equals(userId);
        if (isAssignedAgent) {
            return;
        }
        
        boolean isOwner = property.getOwnerId() != null && property.getOwnerId().equals(userId);
        if (isOwner) {
            return;
        }
        
        if (roles.contains("ROLE_AGENT") || roles.contains("ROLE_CLIENT")) {
            return;
        }
        
        throw new AccessDeniedException("You don't have permission to access these images");
    }

    private boolean isValidImageType(String mimeType, String fileName) {
        if (mimeType != null && ALLOWED_IMAGE_TYPES.contains(mimeType.toLowerCase())) {
            return true;
        }
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return Set.of("jpg", "jpeg", "png", "webp", "heic", "heif").contains(ext);
    }

    private String buildImageObjectKey(String propertyId, String fileName) {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String safeFileName = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
        return String.format("properties/%s/images/%s_%s", propertyId, timestamp, safeFileName);
    }

    private String getPublicUrl(String objectKey) {
        return String.format("http://localhost:9000/%s/%s", imagesBucket, objectKey);
    }

    private void ensureImagesBucketExists() {
        try {
            boolean found = minioClient.bucketExists(
                io.minio.BucketExistsArgs.builder().bucket(imagesBucket).build()
            );
            if (!found) {
                minioClient.makeBucket(
                    io.minio.MakeBucketArgs.builder().bucket(imagesBucket).build()
                );
                log.info("Created images bucket: {}", imagesBucket);
            }
        } catch (Exception e) {
            log.error("Error ensuring images bucket exists: {}", e.getMessage());
        }
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? (String) auth.getPrincipal() : "unknown";
    }

    private List<String> getCurrentUserRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    private Set<String> getCurrentUserRolesSet() {
        return new HashSet<>(getCurrentUserRoles());
    }

    private String getCurrentUserName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        }
        return getCurrentUserId();
    }

    private ImageResponse toImageResponse(ImageMetadata image, String temporaryUrl) {
        return ImageResponse.builder()
                .id(image.getId())
                .originalFileName(image.getOriginalFileName())
                .objectKey(image.getObjectKey())
                .publicUrl(image.getPublicUrl())
                .fileSize(image.getFileSize())
                .mimeType(image.getMimeType())
                .width(image.getWidth())
                .height(image.getHeight())
                .isPrimary(image.getIsPrimary())
                .displayOrder(image.getDisplayOrder())
                .uploadedAt(image.getUploadedAt())
                .uploadedBy(image.getUploadedBy())
                .uploadedByName(image.getUploadedByName())
                .status(image.getStatus())
                .accessPolicy(image.getAccessPolicy())
                .temporaryDownloadUrl(temporaryUrl)
                .expiresInSeconds(presignedExpiryMinutes * 60)
                .build();
    }
}