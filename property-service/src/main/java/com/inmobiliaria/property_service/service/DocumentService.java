package com.inmobiliaria.property_service.service;

import com.inmobiliaria.property_service.domain.DocumentMetadata;
import com.inmobiliaria.property_service.domain.PropertyDocument;
import com.inmobiliaria.property_service.dto.request.ConfirmUploadRequest;
import com.inmobiliaria.property_service.dto.request.GenerateUploadUrlRequest;
import com.inmobiliaria.property_service.dto.response.DocumentResponse;
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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final MinioClient minioClient;
    private final PropertyRepository propertyRepository;

    @Value("${minio.presigned.expiry-minutes:15}")
    private int presignedExpiryMinutes;

    @Value("${minio.documents.bucket:documents}")
    private String documentsBucket;

    // Allowed file types for exclusivity contracts
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * US1: Generate presigned URL for uploading a document
     * Validates file type and size before issuing the URL
     */
    public Map<String, String> generatePresignedUploadUrl(GenerateUploadUrlRequest request) {
        // Validate file type (US1 AC2)
        if (!isValidFileType(request.getMimeType(), request.getFileName())) {
            throw new ValidationException(
                "Invalid file type. Only PDF and Word documents are allowed. " +
                "Received: " + (request.getMimeType() != null ? request.getMimeType() : "unknown")
            );
        }
        
        // Validate file size (US1 AC2)
        if (request.getFileSize() > MAX_FILE_SIZE) {
            throw new ValidationException(
                String.format("File size exceeds limit. Maximum allowed: %d MB, Your file: %.2f MB",
                    MAX_FILE_SIZE / (1024 * 1024),
                    request.getFileSize() / (1024.0 * 1024))
            );
        }
        
        // Verify property exists and user has permission
        PropertyDocument property = propertyRepository.findById(request.getPropertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + request.getPropertyId()));
        
        String currentUserId = getCurrentUserId();
        List<String> roles = getCurrentUserRoles();
        boolean isAdmin = roles.contains("ROLE_ADMIN");
        boolean isAssignedAgent = property.getAssignedAgentId() != null && 
                                  property.getAssignedAgentId().equals(currentUserId);
        
        if (!isAdmin && !isAssignedAgent) {
            throw new AccessDeniedException("You don't have permission to upload documents for this property");
        }
        
        // Ensure bucket exists
        ensureDocumentsBucketExists();
        
        // Generate object key
        String objectKey = buildDocumentObjectKey(
            request.getPropertyId(),
            request.getDocumentType(),
            request.getFileName()
        );
        
        try {
            // Generate presigned URL for PUT operation (US1)
            String uploadUrl = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(documentsBucket)
                    .object(objectKey)
                    .expiry(presignedExpiryMinutes, TimeUnit.MINUTES)
                    .build()
            );
            
            log.info("Generated presigned upload URL for property {}: {}", request.getPropertyId(), objectKey);
            
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

    /**
     * US1: Confirm successful upload and register document in MongoDB
     * Updates property status to "Contracted" for exclusivity contracts (US1 AC3)
     */
    public DocumentResponse confirmUpload(ConfirmUploadRequest request) {
        // Verify property exists
        PropertyDocument property = propertyRepository.findById(request.getPropertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + request.getPropertyId()));
        
        // Verify the object exists in MinIO
        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(documentsBucket)
                    .object(request.getObjectKey())
                    .build()
            );
        } catch (Exception e) {
            log.error("Object not found in MinIO: {}", request.getObjectKey());
            throw new ValidationException("File upload not confirmed. Please try uploading again.");
        }
        
        String currentUserId = getCurrentUserId();
        String currentUserName = getCurrentUserName();
        
        // Create document metadata
        DocumentMetadata document = DocumentMetadata.builder()
                .id(UUID.randomUUID().toString())
                .documentType(request.getDocumentType())
                .originalFileName(request.getOriginalFileName())
                .objectKey(request.getObjectKey())
                .publicUrl(getPublicUrl(request.getObjectKey()))
                .fileSize(request.getFileSize())
                .mimeType(request.getMimeType())
                .uploadedAt(Instant.now())
                .uploadedBy(currentUserId)
                .uploadedByName(currentUserName)
                .status(DocumentMetadata.DocumentStatus.PENDING)
                .accessPolicy(new HashSet<>()) // Initially empty, admin will set permissions
                .build();
        
        // Add document to property
        if (property.getDocuments() == null) {
            property.setDocuments(new ArrayList<>());
        }
        property.getDocuments().add(document);
        
        // US1 AC3: Update property status for exclusivity contract
        if ("EXCLUSIVITY_CONTRACT".equals(request.getDocumentType())) {
            property.setStatus("CONTRACTED");
            log.info("Property {} status updated to CONTRACTED after exclusivity contract upload", property.getId());
        }
        
        property.setUpdatedAt(Instant.now());
        PropertyDocument saved = propertyRepository.save(property);
        
        // Find and return the saved document
        DocumentMetadata savedDoc = saved.getDocuments().stream()
                .filter(d -> d.getId().equals(document.getId()))
                .findFirst()
                .orElseThrow();
        
        log.info("Document confirmed and registered: {} for property {}", document.getId(), request.getPropertyId());
        
        return toResponse(savedDoc, null);
    }

    /**
     * US1 & US2: Get all documents for a property with temporary download URLs
     * Permission check before generating any presigned GET URL
     */
    public List<DocumentResponse> getPropertyDocuments(String propertyId) {
        PropertyDocument property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found: " + propertyId));
        
        // Check permission (US2)
        checkDocumentAccessPermission(property, null);
        
        List<DocumentResponse> responses = new ArrayList<>();
        for (DocumentMetadata doc : property.getDocuments()) {
            String tempUrl = generateTemporaryDownloadUrlForDocument(doc, property);
            responses.add(toResponse(doc, tempUrl));
        }
        
        return responses;
    }

    /**
     * US1 & US2: Get a specific document with temporary download URL
     * Permission check before generating presigned GET URL
     */
    public DocumentResponse getDocument(String documentId) {
        // Find document across all properties
        for (PropertyDocument property : propertyRepository.findAll()) {
            if (property.getDocuments() != null) {
                Optional<DocumentMetadata> docOpt = property.getDocuments().stream()
                        .filter(d -> d.getId().equals(documentId))
                        .findFirst();
                
                if (docOpt.isPresent()) {
                    DocumentMetadata doc = docOpt.get();
                    // Check permission (US2)
                    checkDocumentAccessPermission(property, doc);
                    
                    String tempUrl = generateTemporaryDownloadUrlForDocument(doc, property);
                    return toResponse(doc, tempUrl);
                }
            }
        }
        
        throw new ResourceNotFoundException("Document not found: " + documentId);
    }

    /**
     * US2: Update document access permissions (Admin only)
     */
    public DocumentResponse updateDocumentPermissions(String documentId, Set<String> accessPolicy) {
        for (PropertyDocument property : propertyRepository.findAll()) {
            if (property.getDocuments() != null) {
                Optional<DocumentMetadata> docOpt = property.getDocuments().stream()
                        .filter(d -> d.getId().equals(documentId))
                        .findFirst();
                
                if (docOpt.isPresent()) {
                    DocumentMetadata doc = docOpt.get();
                    doc.setAccessPolicy(accessPolicy != null ? accessPolicy : new HashSet<>());
                    property.setUpdatedAt(Instant.now());
                    propertyRepository.save(property);
                    
                    log.info("Updated permissions for document {}: {}", documentId, accessPolicy);
                    return getDocument(documentId);
                }
            }
        }
        
        throw new ResourceNotFoundException("Document not found: " + documentId);
    }

    /**
     * US2: Generate a new temporary download URL for an existing document
     * AC4: When the download link expires, user must request a new one
     */
    public String generateTemporaryDownloadUrl(String documentId) {
        // Find document
        for (PropertyDocument property : propertyRepository.findAll()) {
            if (property.getDocuments() != null) {
                Optional<DocumentMetadata> docOpt = property.getDocuments().stream()
                        .filter(d -> d.getId().equals(documentId))
                        .findFirst();
                
                if (docOpt.isPresent()) {
                    DocumentMetadata doc = docOpt.get();
                    // Check permission (US2)
                    checkDocumentAccessPermission(property, doc);
                    
                    return generateTemporaryDownloadUrlForDocument(doc, property);
                }
            }
        }
        
        throw new ResourceNotFoundException("Document not found: " + documentId);
    }

    /**
     * Delete a document (Admin only)
     * Also removes the file from MinIO
     */
    public void deleteDocument(String documentId) {
        for (PropertyDocument property : propertyRepository.findAll()) {
            if (property.getDocuments() != null) {
                Optional<DocumentMetadata> docOpt = property.getDocuments().stream()
                        .filter(d -> d.getId().equals(documentId))
                        .findFirst();
                
                if (docOpt.isPresent()) {
                    DocumentMetadata doc = docOpt.get();
                    
                    // Delete from MinIO
                    try {
                        minioClient.removeObject(
                            io.minio.RemoveObjectArgs.builder()
                                .bucket(documentsBucket)
                                .object(doc.getObjectKey())
                                .build()
                        );
                        log.info("Deleted file from MinIO: {}", doc.getObjectKey());
                    } catch (Exception e) {
                        log.error("Failed to delete file from MinIO: {}", e.getMessage());
                        // Continue to remove reference even if file deletion fails
                    }
                    
                    // Remove from property
                    property.getDocuments().removeIf(d -> d.getId().equals(documentId));
                    property.setUpdatedAt(Instant.now());
                    propertyRepository.save(property);
                    
                    log.info("Deleted document: {}", documentId);
                    return;
                }
            }
        }
        
        throw new ResourceNotFoundException("Document not found: " + documentId);
    }

    /**
     * US2: Check if current user has permission to access a document
     * Implements role-based permission logic (Admin, Assigned Agent, General Agent)
     * AC1: If user doesn't have permission, they receive "Access Denied"
     */
    private void checkDocumentAccessPermission(PropertyDocument property, DocumentMetadata document) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        
        String userId = getCurrentUserId();
        Set<String> roles = getCurrentUserRolesSet();
        
        // Admin has full access (US2)
        if (roles.contains("ROLE_ADMIN")) {
            return;
        }
        
        // Check if user is the assigned agent for this property
        boolean isAssignedAgent = property.getAssignedAgentId() != null && 
                                  property.getAssignedAgentId().equals(userId);
        
        if (isAssignedAgent) {
            return;
        }
        
        // Check document-specific access policy (if set)
        if (document != null && document.getAccessPolicy() != null && !document.getAccessPolicy().isEmpty()) {
            // Check if user's roles match any policy (ROLE_XXX)
            for (String policy : document.getAccessPolicy()) {
                if (policy.startsWith("ROLE_") && roles.contains(policy)) {
                    return;
                }
                // Check if specific user ID is in policy
                if (policy.equals(userId)) {
                    return;
                }
            }
        }
        
        // Owner can access their own property documents
        boolean isOwner = property.getOwnerId() != null && property.getOwnerId().equals(userId);
        if (isOwner) {
            return;
        }
        
        // General agent permission check (if property has ROLE_AGENT in access policy)
        if (document != null && document.getAccessPolicy() != null && 
            document.getAccessPolicy().contains("ROLE_AGENT") && 
            (roles.contains("ROLE_AGENT") || roles.contains("ROLE_AGENT"))) {
            return;
        }
        
        // AC1: Access denied
        throw new AccessDeniedException("You don't have permission to access this document");
    }

    /**
     * Generate a temporary download URL for a document (US1 AC1, US2 AC2)
     * AC4: URL expires after configured time
     */
    private String generateTemporaryDownloadUrlForDocument(DocumentMetadata document, PropertyDocument property) {
        try {
            // Generate presigned GET URL with expiration (US1 AC4)
            String url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(documentsBucket)
                    .object(document.getObjectKey())
                    .expiry(presignedExpiryMinutes, TimeUnit.MINUTES)
                    .build()
            );
            
            log.debug("Generated temporary download URL for document: {}", document.getId());
            return url;
        } catch (Exception e) {
            log.error("Error generating download URL: {}", e.getMessage());
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }

    public int getPresignedExpirySeconds() {
        return presignedExpiryMinutes * 60;
    }

    // ==================== Helper Methods ====================

    private boolean isValidFileType(String mimeType, String fileName) {
        if (mimeType != null && ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            return true;
        }
        // Fallback to file extension check
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return Set.of("pdf", "doc", "docx").contains(ext);
    }

    private String buildDocumentObjectKey(String propertyId, String documentType, String fileName) {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String safeFileName = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
        return String.format("properties/%s/%s/%s_%s", propertyId, documentType.toLowerCase(), timestamp, safeFileName);
    }

    private String getPublicUrl(String objectKey) {
        return String.format("http://localhost:9000/%s/%s", documentsBucket, objectKey);
    }

    private void ensureDocumentsBucketExists() {
        try {
            boolean found = minioClient.bucketExists(
                io.minio.BucketExistsArgs.builder().bucket(documentsBucket).build()
            );
            if (!found) {
                minioClient.makeBucket(
                    io.minio.MakeBucketArgs.builder().bucket(documentsBucket).build()
                );
                log.info("Created documents bucket: {}", documentsBucket);
                
                // Set private bucket policy (AC3: block direct public access)
                String privatePolicy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Deny\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + documentsBucket + "/*\"]}]}";
                minioClient.setBucketPolicy(
                    io.minio.SetBucketPolicyArgs.builder()
                        .bucket(documentsBucket)
                        .config(privatePolicy)
                        .build()
                );
                log.info("Set private bucket policy for: {}", documentsBucket);
            }
        } catch (Exception e) {
            log.error("Error ensuring documents bucket exists: {}", e.getMessage());
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
        // Try to get name from principal details
        Object principal = auth.getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        }
        return getCurrentUserId();
    }

    private DocumentResponse toResponse(DocumentMetadata document, String temporaryUrl) {
        return DocumentResponse.builder()
                .id(document.getId())
                .documentType(document.getDocumentType())
                .originalFileName(document.getOriginalFileName())
                .objectKey(document.getObjectKey())
                .publicUrl(document.getPublicUrl())
                .fileSize(document.getFileSize())
                .mimeType(document.getMimeType())
                .uploadedAt(document.getUploadedAt())
                .uploadedBy(document.getUploadedBy())
                .uploadedByName(document.getUploadedByName())
                .status(document.getStatus())
                .accessPolicy(document.getAccessPolicy())
                .validUntil(document.getValidUntil())
                .temporaryDownloadUrl(temporaryUrl)
                .expiresInSeconds(presignedExpiryMinutes * 60)
                .build();
    }
}