package com.inmobiliaria.operation_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;

/**
 * MinIO configuration.
 *
 * Creates the {@link MinioClient} bean and ensures the receipts bucket
 * exists on application startup. If the bucket is missing it is created
 * automatically — no manual MinIO setup required beyond having the
 * server running.
 *
 * Properties are read from application.yml:
 * minio.endpoint — e.g. http://localhost:9000
 * minio.access-key — MinIO root user
 * minio.secret-key — MinIO root password
 * minio.bucket-name — target bucket (default: operations-payment-receipts)
 */
@Slf4j
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        ensureBucketExists(client);
        return client;
    }

    /**
     * Creates the receipts bucket if it does not already exist.
     * Called once at startup — safe to run on every boot.
     */
    private void ensureBucketExists(MinioClient client) {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());

            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("[MinIO] Bucket '{}' created successfully.", bucketName);
            } else {
                log.info("[MinIO] Bucket '{}' already exists.", bucketName);
            }
        } catch (Exception e) {
            // Log but do not crash startup — service can still run if MinIO is temporarily
            // unavailable
            log.error("[MinIO] Failed to verify/create bucket '{}': {}", bucketName, e.getMessage());
        }
    }
}