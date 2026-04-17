package com.inmobiliaria.operation_service;

import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import jakarta.annotation.PostConstruct;

/**
 * Operation Service — Inmobiliaria
 *
 * <p>Manages real-estate operations and their associated payment receipts. Receipts (PDF / images)
 * are stored in MinIO object storage.
 *
 * <p>Port : 8087 Eureka: registered as "operation-service"
 */
@SpringBootApplication
@EnableDiscoveryClient
public class OperationServiceApplication {

  @PostConstruct
  public void init() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
    return builder -> {
      builder.serializers(
          new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));
    };
  }

  public static void main(String[] args) {
    SpringApplication.run(OperationServiceApplication.class, args);
  }
}
