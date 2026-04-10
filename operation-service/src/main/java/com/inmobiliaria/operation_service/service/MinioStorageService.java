package com.inmobiliaria.operation_service.service;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles all interactions with MinIO object storage.
 *
 * Responsibilities:
 * - Upload a receipt file and return its object key.
 * - Generate a pre-signed GET URL so the frontend can download the file
 * directly.
 * - Delete a file from the bucket when a receipt is removed.
 *
 * Object key format: receipts/{operationId}/{uuid}.{extension}
 * This keeps each operation's files logically grouped inside the bucket.
 */
@Slf4j
@Service
public class MinioStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /** Pre-signed URL expiry — 1 hour is a safe default for download links */
    private static final int PRESIGNED_URL_EXPIRY_HOURS = 1;

    // ── Allowed file types (PA: only valid receipt formats accepted) ──────────
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp");

    /** Maximum file size: 10 MB */
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    public MinioStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPLOAD
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates and uploads a receipt file to MinIO.
     *
     * @param file        The uploaded multipart file
     * @param operationId The operation the file belongs to
     * @return The MinIO object key (stored in the Receipt document)
     * @throws IllegalArgumentException if the file type or size is not allowed
     * @throws RuntimeException         if the MinIO upload fails
     */
    public String uploadFile(MultipartFile file, String operationId) {
        validateFile(file);

        String objectKey = buildObjectKey(operationId, file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
            log.info("[MinIO] File uploaded successfully: bucket='{}', key='{}'", bucketName, objectKey);
            return objectKey;

        } catch (Exception e) {
            log.error("[MinIO] Upload failed for key '{}': {}", objectKey, e.getMessage());
            throw new RuntimeException("File upload to MinIO failed: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRE-SIGNED DOWNLOAD URL
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a pre-signed GET URL for the given object key.
     * The URL is valid for {@value PRESIGNED_URL_EXPIRY_HOURS} hour(s).
     *
     * @param objectKey MinIO object key stored in the Receipt document
     * @return Pre-signed URL string, or null if URL generation fails
     */
    public String generatePresignedUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank())
            return null;
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(PRESIGNED_URL_EXPIRY_HOURS, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            log.warn("[MinIO] Failed to generate pre-signed URL for key '{}': {}", objectKey, e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Deletes a file from MinIO by its object key.
     * Errors are logged but not re-thrown — the database record should still
     * be deleted even if the MinIO object is already gone.
     *
     * @param objectKey MinIO object key to delete
     */
    public void deleteFile(String objectKey) {
        if (objectKey == null || objectKey.isBlank())
            return;
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
            log.info("[MinIO] File deleted successfully: key='{}'", objectKey);
        } catch (Exception e) {
            log.warn("[MinIO] Failed to delete file '{}': {}", objectKey, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VALIDATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates content type and file size.
     * Throws {@link IllegalArgumentException} with a user-friendly message on
     * failure.
     * This is the enforcement point for PA: "only valid receipt formats accepted."
     */
    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was provided or the file is empty.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Invalid file format. Only PDF, JPEG, PNG, and WebP files are accepted. " +
                            "Received: " + contentType);
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "File is too large. Maximum allowed size is 10 MB. " +
                            "Received: " + (file.getSize() / (1024 * 1024)) + " MB.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the MinIO object key.
     * Format: receipts/{operationId}/{uuid}.{extension}
     */
    private String buildObjectKey(String operationId, String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = "." + originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
        }
        return "receipts/" + operationId + "/" + UUID.randomUUID() + extension;
    }
}