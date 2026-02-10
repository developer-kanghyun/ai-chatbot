package com.example.chatbot.conversation.dto;

import com.example.chatbot.entity.Conversation;
import com.example.chatbot.entity.Message;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ConversationDetailResponse {
    private String id;
    private String title;
    private List<MessageDto> messages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static ConversationDetailResponse from(Conversation conversation, List<Message> messages) {
        return ConversationDetailResponse.builder()
                .id(String.valueOf(conversation.getId()))
                .title(conversation.getTitle())
                .messages(messages.stream().map(MessageDto::from).collect(Collectors.toList()))
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class MessageDto {
        private String id;
        private String role;
        private String content;
        private LocalDateTime createdAt;

        public static MessageDto from(Message message) {
            return MessageDto.builder()
                    .id(String.valueOf(message.getId()))
                    .role(message.getRole().name().toLowerCase())
                    .content(message.getContent())
                    .createdAt(message.getCreatedAt())
                    .build();
        }
    }
}
