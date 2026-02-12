package com.example.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "openai")
public class OpenAiConfig {

    private String apiKey;
    private String baseUrl;
    private String model;
    private int timeoutMs;

    @Bean
    public WebClient openAiWebClient(WebClient.Builder builder) {
        // HttpClient 설정 (Timeout 적용)
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeoutMs));

        return builder
                .baseUrl(java.util.Objects.requireNonNull(baseUrl))
                .clientConnector(new ReactorClientHttpConnector(java.util.Objects.requireNonNull(httpClient)))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }
}
