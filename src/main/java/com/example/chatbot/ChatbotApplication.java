package com.example.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class ChatbotApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatbotApplication.class, args);
	}

}
