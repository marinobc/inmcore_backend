package com.inmobiliaria.property_service.controller;

import com.inmobiliaria.property_service.dto.request.ConfirmUploadRequest;
import com.inmobiliaria.property_service.dto.request.GenerateUploadUrlRequest;
import com.inmobiliaria.property_service.dto.request.UpdateDocumentPermissionsRequest;
import com.inmobiliaria.property_service.dto.response.DocumentResponse;
import com.inmobiliaria.property_service.service.DocumentService;
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
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * US1: Generate presigned URL for uploading a document
     * Validates file type and size before issuing the URL
     */
    @PostMapping("/upload-url")
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public Map<String, String> generateUploadUrl(@Valid @RequestBody GenerateUploadUrlRequest request) {
        log.info("Generating upload URL for property: {}, type: {}, file: {}", 
                request.getPropertyId(), request.getDocumentType(), request.getFileName());
        return documentService.generatePresignedUploadUrl(request);
    }

    /**
     * US1: Confirm successful upload and register document in MongoDB
     * Updates property status to "Contracted" for exclusivity contracts
     */
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public DocumentResponse confirmUpload(@Valid @RequestBody ConfirmUploadRequest request) {
        log.info("Confirming upload for property: {}, document: {}", 
                request.getPropertyId(), request.getObjectKey());
        return documentService.confirmUpload(request);
    }

    /**
     * US1 & US2: Get all documents for a property with temporary download URLs
     * Permission check before generating any presigned GET URL
     */
    @GetMapping("/property/{propertyId}")
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN') or hasRole('OWNER')")
    public List<DocumentResponse> getPropertyDocuments(@PathVariable String propertyId) {
        log.info("Fetching documents for property: {}", propertyId);
        return documentService.getPropertyDocuments(propertyId);
    }

    /**
     * US1 & US2: Get a specific document with temporary download URL
     * Permission check before generating presigned GET URL
     */
    @GetMapping("/{documentId}")
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN') or hasRole('OWNER')")
    public DocumentResponse getDocument(@PathVariable String documentId) {
        log.info("Fetching document: {}", documentId);
        return documentService.getDocument(documentId);
    }

    /**
     * US2: Update document access permissions (Admin only)
     */
    @PatchMapping("/{documentId}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public DocumentResponse updateDocumentPermissions(
            @PathVariable String documentId,
            @Valid @RequestBody UpdateDocumentPermissionsRequest request) {
        log.info("Updating permissions for document: {}", documentId);
        return documentService.updateDocumentPermissions(documentId, request.getAccessPolicy());
    }

    /**
     * US2: Generate a new temporary download URL for an existing document
     * Useful when previous URL expired
     */
    @PostMapping("/{documentId}/refresh-url")
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN') or hasRole('OWNER')")
    public Map<String, String> refreshDownloadUrl(@PathVariable String documentId) {
        log.info("Refreshing download URL for document: {}", documentId);
        String url = documentService.generateTemporaryDownloadUrl(documentId);
        return Map.of(
                "temporaryDownloadUrl", url,
                "expiresInSeconds", String.valueOf(documentService.getPresignedExpirySeconds())
        );
    }

    /**
     * Delete a document (Admin only)
     */
    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteDocument(@PathVariable String documentId) {
        log.info("Deleting document: {}", documentId);
        documentService.deleteDocument(documentId);
    }
}