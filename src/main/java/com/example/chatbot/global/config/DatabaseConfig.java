package com.example.chatbot.global.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    public DataSourceProperties dataSourceProperties(DataSourceProperties properties) {
        String url = properties.getUrl();
        if (url != null && url.startsWith("postgres://")) {
            // Render의 'postgres://' 형식을 Spring Boot의 'jdbc:postgresql://' 형식으로 변환
            String updatedUrl = url.replace("postgres://", "jdbc:postgresql://");
            properties.setUrl(updatedUrl);
        }
        return properties;
    }
}
