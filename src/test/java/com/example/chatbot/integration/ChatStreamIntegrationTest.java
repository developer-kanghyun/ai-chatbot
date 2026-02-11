package com.example.chatbot.integration;

import com.example.chatbot.conversation.repository.ConversationRepository;
import com.example.chatbot.conversation.repository.MessageRepository;
import com.example.chatbot.entity.Conversation;
import com.example.chatbot.entity.Message;
import com.example.chatbot.entity.User;
import com.example.chatbot.repository.UserRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
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
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Architecture Note]
 * - Server: Spring Boot MVC (Tomcat) on RANDOM_PORT. 실제 HTTP 서버 구동.
 * - Client: WebTestClient가 실제 HTTP 요청을 전송 (bindToServer 모드).
 * - DB: Testcontainers PostgreSQL (운영 환경과 동일).
 * - Redis: Testcontainers GenericContainer (redis:7-alpine).
 * - OpenAI: MockWebServer가 외부 API를 대체하여 요청 캡처 + 고정 응답.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30000")
@ActiveProfiles("test")
@Testcontainers
class ChatStreamIntegrationTest {

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

    // --- MockWebServer (OpenAI) ---
    static MockWebServer mockWebServer;

    @BeforeAll
    static void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

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

        // OpenAI → MockWebServer
        registry.add("openai.base-url", () -> mockWebServer.url("/").toString());
        registry.add("openai.api-key", () -> "test-key");
        registry.add("openai.model", () -> "gpt-4o-mini");
        registry.add("openai.timeout-ms", () -> "5000");

        // Rate Limit OFF (SSE 테스트 방해 금지)
        registry.add("app.rate-limit.enabled", () -> "false");
    }

    private User testUser;

    @BeforeEach
    void clearData() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        // ChatService가 userId=1L을 하드코딩하므로, ID=1인 User를 강제로 생성해야 함.
        // JPA save()는 시퀀스를 사용하여 ID가 증가하므로, 네이티브 SQL로 ID를 1로 고정하여 삽입.
        jdbcTemplate.update("INSERT INTO users (id, api_key, created_at, updated_at) VALUES (1, 'test-key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        testUser = userRepository.findById(1L).orElseThrow();
    }

    // --- TC1: SSE Happy Path ---
    @Test
    @DisplayName("TC1: SSE 스트리밍 - delta와 done 이벤트가 포함되어야 한다")
    void testSseHappyPath() {
        // Given: MockWebServer에 OpenAI 스트리밍 응답 등록
        String mockBody = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n" +
                          "data: [DONE]\n\n";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(mockBody));

        // When & Then: ServerSentEvent로 역직렬화하여 event name + data 검증
        ParameterizedTypeReference<ServerSentEvent<String>> sseType =
                new ParameterizedTypeReference<>() {};

        webTestClient.post()
                .uri("/api/chat/completions/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .bodyValue("{\"message\": \"Hi\"}")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(sseType)
                .getResponseBody()
                .timeout(Duration.ofSeconds(10))
                .collectList()
                .as(StepVerifier::create)
                .consumeNextWith(events -> {
                    // ping 이벤트 제외하고 의미있는 이벤트만 추출 (token, done)
                    List<String> eventNames = events.stream()
                            .map(ServerSentEvent::event)
                            .filter(e -> e != null && !e.equals("ping"))
                            .toList();

                    // token 이벤트 포함 검증
                    assertThat(eventNames).contains("token");
                    // done 이벤트 포함 검증
                    assertThat(eventNames).contains("done");

                    // token 이벤트의 data 검증 ({"text":"Hello"})
                    List<String> tokenData = events.stream()
                            .filter(e -> "token".equals(e.event()))
                            .map(ServerSentEvent::data)
                            .toList();
                    assertThat(tokenData).anyMatch(d -> d.contains("{\"text\":\"Hello\"}"));

                    // done 이벤트의 data 검증 ({})
                    List<String> doneData = events.stream()
                            .filter(e -> "done".equals(e.event()))
                            .map(ServerSentEvent::data)
                            .toList();
                    assertThat(doneData).contains("{}");
                })
                .verifyComplete();
    }

    // --- TC3: Context Inclusion ---
    @Test
    @DisplayName("TC3: Context - OpenAI 요청 바디에 이전 메시지가 포함되어야 한다")
    void testContextInclusion() throws InterruptedException {
        // Given: DB에 이전 대화와 메시지를 미리 저장
        Conversation conv = conversationRepository.save(new Conversation(testUser, "Context Test"));
        messageRepository.save(new Message(conv, Message.Role.user, "OldUserMsg"));
        messageRepository.save(new Message(conv, Message.Role.assistant, "OldAiMsg"));

        // MockWebServer에 즉시 완료 응답 등록
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: [DONE]\n\n"));

        // When: 스트리밍 API 호출 — ServerSentEvent로 수신하여 실행 보장
        ParameterizedTypeReference<ServerSentEvent<String>> sseType =
                new ParameterizedTypeReference<>() {};

        webTestClient.post()
                .uri("/api/chat/completions/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-API-Key", "test-key")
                .bodyValue("{\"conversation_id\": \"" + conv.getId() + "\", \"message\": \"NewMsg\"}")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(sseType)
                .getResponseBody()
                .timeout(Duration.ofSeconds(10))
                .collectList()
                .block();

        // Then: MockWebServer가 받은 OpenAI 요청 바디 검증
        RecordedRequest request = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        String body = request.getBody().readUtf8();

        assertThat(body).contains("OldUserMsg");
        assertThat(body).contains("OldAiMsg");
        assertThat(body).contains("NewMsg");
    }
}
