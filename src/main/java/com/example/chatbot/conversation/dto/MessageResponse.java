package com.example.chatbot.conversation.dto;

import com.example.chatbot.entity.Message;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MessageResponse {
    private String id;
    private String role;
    private String content;
    private LocalDateTime createdAt;

    public static MessageResponse from(Message message) {
        return MessageResponse.builder()
                .id(String.valueOf(message.getId()))
                .role(message.getRole().name().toLowerCase())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
