package com.balancdapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BalancdAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(BalancdAppApplication.class, args);
	}

}
