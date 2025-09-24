package com.hukisanagi.springboot_bookmark_manager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpringbootBookmarkManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringbootBookmarkManagerApplication.class, args);
	}
}
