package com.example.chatbot.integration;

import com.example.chatbot.conversation.repository.ConversationRepository;
import com.example.chatbot.conversation.repository.MessageRepository;
import com.example.chatbot.entity.User;
import com.example.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Rate Limit 통합 테스트.
 * - limit=3으로 설정, 4번째 요청에서 429 + Retry-After 검증.
 * - SSE/Stream 테스트와 분리하여 설정 간섭을 방지.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30000")
@ActiveProfiles("test")
@Testcontainers
class RateLimitIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;
    
    @Autowired
    private ConversationRepository conversationRepository;
    
    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // --- Testcontainers ---
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    // --- Property Injection (Explicit) ---
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // DB
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // Rate Limit ON (Limit=3)
        registry.add("app.rate-limit.enabled", () -> "true");
        registry.add("app.rate-limit.limit", () -> "3");
        registry.add("app.rate-limit.window-seconds", () -> "60");
    }

    @BeforeEach
    void setupAndFlushRedis() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        // User(id=1) 생성 (ConversationService가 userId=1L 하드코딩)
        // JPA save() 대신 네이티브 SQL로 ID=1 강제 삽입
        jdbcTemplate.update("INSERT INTO users (id, api_key, created_at, updated_at) VALUES (1, 'test-key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        userRepository.findById(1L).ifPresent(u -> {}); // 확인용 조회 (Optional)

        // Redis Flush (실패 시 테스트 중단)
        try {
            org.testcontainers.containers.Container.ExecResult result = redis.execInContainer("redis-cli", "flushall");
            if (result.getExitCode() != 0) {
                fail("Redis flush failed: " + result.getStderr());
            }
        } catch (Exception e) {
            fail("Redis flush error: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("TC2: Rate Limiting - 4번째 요청 차단 (429 + Retry-After)")
    void testRateLimiting() {
        // Given: Limit=3 (DynamicPropertySource), Redis flushed (@BeforeEach)

        // When: 1~3 calls (Should succeed with 200 OK)
        for (int i = 0; i < 3; i++) {
            webTestClient.get().uri("/api/conversations")
                    .header("X-API-Key", "test-key")
                    .exchange()
                    .expectStatus().isOk();
        }

        // Then: 4th call should return 429 Too Many Requests
        webTestClient.get().uri("/api/conversations")
                .header("X-API-Key", "test-key")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After");
    }
}
