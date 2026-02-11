package com.example.chatbot.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConversationUpdateRequest {
    @NotBlank(message = "제목은 비어있을 수 없습니다.")
    private String title;
}
