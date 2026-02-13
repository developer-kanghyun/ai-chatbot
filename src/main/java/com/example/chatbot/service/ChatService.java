package com.example.chatbot.service;

import com.example.chatbot.dto.request.ChatCompletionRequest;
import com.example.chatbot.dto.response.ChatCompletionResponse;
import com.example.chatbot.dto.openai.OpenAiMessage;
import com.example.chatbot.entity.Conversation;
import com.example.chatbot.entity.Message;
import com.example.chatbot.global.error.AppException;
import com.example.chatbot.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationContextService conversationContextService;
    private final OpenAiService openAiService;

    @Transactional
    public ChatCompletionResponse createChatCompletion(ChatCompletionRequest request, Long userId) {
        Long conversationId = parseConversationId(request.getConversationId());
        Conversation conversation = conversationContextService.getOrCreateConversation(conversationId, userId, request.getMessage());

        conversationContextService.saveUserMessage(conversation, request.getMessage());

        List<OpenAiMessage> openAiMessages =
                conversationContextService.buildOpenAiContextMessages(conversation.getId());

        String assistantContent = openAiService.createChatCompletion(openAiMessages);

        Message assistantMessage = conversationContextService.saveAssistantMessage(conversation, assistantContent);

        return ChatCompletionResponse.builder()
                .conversationId(String.valueOf(conversation.getId()))
                .message(ChatCompletionResponse.MessageDto.builder()
                        .id(String.valueOf(assistantMessage.getId()))
                        .role(assistantMessage.getRole().name())
                        .content(assistantMessage.getContent())
                        .createdAt(assistantMessage.getCreatedAt())
                        .build())
                .build();
    }

    public SseEmitter createChatCompletionStream(ChatCompletionRequest request, Long userId) {
        SseEmitter emitter = new SseEmitter(60000L); 
        Long conversationId = parseConversationId(request.getConversationId());
        Conversation conversation = conversationContextService.getOrCreateConversation(conversationId, userId, request.getMessage());

        conversationContextService.saveUserMessage(conversation, request.getMessage());

        List<OpenAiMessage> openAiMessages =
                conversationContextService.buildOpenAiContextMessages(conversation.getId());

        StringBuilder gatheredContent = new StringBuilder();

        openAiService.createChatCompletionStream(openAiMessages)
                .subscribe(
                        content -> {
                            if (content != null) {
                                gatheredContent.append(content);
                                try {
                                    Object eventData = Collections.singletonMap("text", content);
                                    emitter.send(SseEmitter.event()
                                            .name("token")
                                            .data(eventData));
                                } catch (IOException e) {
                                    log.error("SSE send failed", e);
                                }
                            }
                        },
                        streamError -> {
                            log.error("Stream error", streamError);
                            emitter.completeWithError(streamError);
                        },
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                emitter.complete();
                                
                                String fullContent = gatheredContent.toString();
                                if (!fullContent.isEmpty()) {
                                    conversationContextService.saveAssistantMessage(conversation, fullContent);
                                }
                            } catch (IOException e) {
                                log.error("SSE complete failed", e);
                            }
                        }
                );

        return emitter;
    }

    private Long parseConversationId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new AppException(ErrorCode.INVALID_INPUT, "conversation_id는 숫자여야 합니다.");
        }
    }
}
