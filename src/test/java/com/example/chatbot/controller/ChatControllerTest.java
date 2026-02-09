package com.example.chatbot.controller;

import com.example.chatbot.dto.request.ChatCompletionRequest;
import com.example.chatbot.dto.response.ChatCompletionResponse;
import com.example.chatbot.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    @Test
    void testEmptyMessageReturns400() throws Exception {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessage(""); // 빈 메시지

        mockMvc.perform(post("/api/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
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
                        .createdAt(Instant.now())
                        .build())
                .build();

        when(chatService.createChatCompletion(any())).thenReturn(mockResponse);

        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setMessage("Hello");
        request.setConversationId(null); // conversation_id 없음

        mockMvc.perform(post("/api/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conversation_id").value("mock-conversation-id"))
                .andExpect(jsonPath("$.data.message.content").value("Mock assistant response"));
    }
}
