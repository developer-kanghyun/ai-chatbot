package com.example.chatbot.controller;

import com.example.chatbot.dto.request.ChatCompletionRequest;
import com.example.chatbot.dto.response.ChatCompletionResponse;
import com.example.chatbot.repository.UserRepository;
import com.example.chatbot.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private ChatService chatService;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("app.rate-limit.enabled", () -> "false");
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        jdbcTemplate.update("INSERT INTO users (id, api_key, created_at, updated_at) VALUES (1, 'test-key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }

    @Test
    void testEmptyMessageReturns400() throws Exception {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessage(""); // 빈 메시지

        mockMvc.perform(post("/api/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "test-key")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void testWithoutConversationIdReturns200AndConversationId() throws Exception {
        // Mock 응답 설정
        ChatCompletionResponse mockResponse = ChatCompletionResponse.builder()
                .conversationId("mock-conversation-id")
                .message(ChatCompletionResponse.MessageDto.builder()
                        .id("mock-message-id")
                        .role("assistant")
                        .content("Mock assistant response")
                        .createdAt(LocalDateTime.now())
                        .build())
                .build();

        when(chatService.createChatCompletion(any(), anyLong())).thenReturn(mockResponse);

        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessage("Hello");
        request.setConversationId(null); // conversation_id 없음

        mockMvc.perform(post("/api/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "test-key")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conversation_id").value("mock-conversation-id"))
                .andExpect(jsonPath("$.data.message.content").value("Mock assistant response"));
    }
}
