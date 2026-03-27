package com.inmobiliaria.property_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class PropertyServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(PropertyServiceApplication.class, args);
	}
}