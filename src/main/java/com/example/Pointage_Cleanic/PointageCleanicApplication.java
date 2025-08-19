package com.example.Pointage_Cleanic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.example.Pointage_Cleanic")
public class PointageCleanicApplication {

	public static void main(String[] args) {

		SpringApplication.run(PointageCleanicApplication.class, args);
	}

}
