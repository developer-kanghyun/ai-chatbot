package com.example.chatbot.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatCompletionRequest {
    
    @NotBlank(message = "message는 필수입니다.")
    private String message;
    
    @JsonProperty("conversation_id")
    private String conversationId; // Optional
}
