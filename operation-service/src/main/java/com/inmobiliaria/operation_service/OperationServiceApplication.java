package com.inmobiliaria.operation_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Operation Service — Inmobiliaria
 *
 * Manages real-estate operations and their associated payment receipts.
 * Receipts (PDF / images) are stored in MinIO object storage.
 *
 * Port : 8087
 * Eureka: registered as "operation-service"
 */
@SpringBootApplication
@EnableDiscoveryClient
public class OperationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OperationServiceApplication.class, args);
    }
}