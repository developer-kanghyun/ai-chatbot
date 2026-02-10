package com.example.chatbot.conversation.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.example.chatbot.entity.Conversation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ConversationListResponse {
    private String id;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static ConversationListResponse from(Conversation conversation) {
        return ConversationListResponse.builder()
                .id(String.valueOf(conversation.getId()))
                .title(conversation.getTitle())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }
    
    public static List<ConversationListResponse> from(List<Conversation> conversations) {
        return conversations.stream()
                .map(ConversationListResponse::from)
                .collect(Collectors.toList());
    }
}
