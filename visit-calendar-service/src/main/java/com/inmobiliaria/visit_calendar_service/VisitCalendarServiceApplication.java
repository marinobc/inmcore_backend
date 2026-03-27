package com.inmobiliaria.visit_calendar_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class VisitCalendarServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(VisitCalendarServiceApplication.class, args);
	}

}
