package com.example.chatbot.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30000")
@ActiveProfiles("test")
@Testcontainers
class RateLimitIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private WebTestClient webTestClient;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.rate-limit.enabled", () -> "true");
        registry.add("app.rate-limit.limit", () -> "3");
        registry.add("app.rate-limit.window-seconds", () -> "60");
    }

    @Test
    @DisplayName("Rate Limiting - 4번째 요청 차단 검증")
    void testRateLimiting() {
        for (int i = 0; i < 3; i++) {
            webTestClient.get().uri("/api/conversations")
                    .header("X-API-Key", "test-key")
                    .exchange()
                    .expectStatus().isOk();
        }

        webTestClient.get().uri("/api/conversations")
                .header("X-API-Key", "test-key")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After");
    }
}
