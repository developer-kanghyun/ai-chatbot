package com.example.chatbot.integration;

import com.example.chatbot.entity.Message;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30000")
@ActiveProfiles("test")
@Testcontainers
class ChatStreamIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private WebTestClient webTestClient;

    private static MockWebServer mockBackEnd;

    @BeforeAll
    static void setUp() throws IOException {
        mockBackEnd = new MockWebServer();
        mockBackEnd.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockBackEnd.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("openai.base-url", () -> String.format("http://localhost:%d", mockBackEnd.getPort()));
        registry.add("openai.api-key", () -> "test-openai-key");
    }

    @Test
    void testChatCompletionStream() {
        mockBackEnd.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\" World\"}}]}\n\ndata: [DONE]\n\n"));

        Flux<String> body = webTestClient.post()
                .uri("/api/chat/completions/stream")
                .header("X-API-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":\"안녕\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class)
                .getResponseBody()
                .take(5);

        List<String> events = body.collectList().block();
        assertThat(events).isNotNull();
        assertThat(events).anyMatch(line -> line.contains("Hello"));
        assertThat(events).anyMatch(line -> line.contains("World"));
        assertThat(events).anyMatch(line -> line.contains("[DONE]"));

        List<Message> messages = messageRepository.findAll();
        assertThat(messages).hasSize(2);
        assertThat(messages)
                .extracting(Message::getRole)
                .containsExactly(Message.Role.user, Message.Role.assistant);
        assertThat(messages.get(1).getContent()).isEqualTo("Hello World");
    }
}
