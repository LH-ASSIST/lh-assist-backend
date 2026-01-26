package com.lh.assist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LhAssistBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(LhAssistBackendApplication.class, args);
	}

}
