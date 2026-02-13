package com.example.chatbot.dto.openai;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class ChatRequest {
    private String model;
    private List<OpenAiMessage> messages;
    @Builder.Default
    private boolean stream = false;
}
