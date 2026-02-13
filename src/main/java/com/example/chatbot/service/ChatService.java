package com.example.chatbot.service;

import com.example.chatbot.conversation.repository.ConversationRepository;
import com.example.chatbot.conversation.repository.MessageRepository;
import com.example.chatbot.dto.request.ChatCompletionRequest;
import com.example.chatbot.dto.response.ChatCompletionResponse;
import com.example.chatbot.entity.Conversation;
import com.example.chatbot.entity.Message;
import com.example.chatbot.entity.User;
import com.example.chatbot.global.error.AppException;
import com.example.chatbot.global.error.ErrorCode;
import com.example.chatbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final OpenAiService openAiService;

    @Transactional
    public ChatCompletionResponse createChatCompletion(ChatCompletionRequest request) {
        Long conversationId = parseConversationId(request.getConversationId());
        Conversation conversation = getOrCreateConversation(conversationId, 1L, request.getMessage());

        messageRepository.save(new Message(conversation, Message.Role.user, request.getMessage()));

        List<Message> contextMessages = getContextMessages(conversation.getId());
        List<com.example.chatbot.dto.openai.Message> openAiMessages = convertToOpenAiMessages(contextMessages);

        String assistantContent = openAiService.createChatCompletion(openAiMessages);

        Message assistantMessage = new Message(conversation, Message.Role.assistant, assistantContent);
        messageRepository.save(assistantMessage);

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

    public SseEmitter createChatCompletionStream(ChatCompletionRequest request) {
        SseEmitter emitter = new SseEmitter(60000L); 
        Long conversationId = parseConversationId(request.getConversationId());
        Conversation conversation = getOrCreateConversation(conversationId, 1L, request.getMessage());

        messageRepository.save(new Message(conversation, Message.Role.user, request.getMessage()));

        List<Message> contextMessages = getContextMessages(conversation.getId());
        List<com.example.chatbot.dto.openai.Message> openAiMessages = convertToOpenAiMessages(contextMessages);

        StringBuilder gatheredContent = new StringBuilder();

        openAiService.createChatCompletionStream(openAiMessages)
                .subscribe(
                        content -> {
                            if (content != null) {
                                gatheredContent.append(content);
                                try {
                                    Object eventData = java.util.Objects.requireNonNull(java.util.Collections.singletonMap("text", content));
                                    emitter.send(SseEmitter.event()
                                            .name("token")
                                            .data(eventData));
                                } catch (IOException e) {
                                    log.error("SSE send failed", e);
                                }
                            }
                        },
                        err -> {
                            if (err != null) {
                                log.error("Stream error", err);
                                emitter.completeWithError(err);
                            } else {
                                emitter.complete();
                            }
                        },
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                emitter.complete();
                                
                                String fullContent = gatheredContent.toString();
                                if (!fullContent.isEmpty()) {
                                    messageRepository.save(new Message(conversation, Message.Role.assistant, fullContent));
                                }
                            } catch (IOException e) {
                                log.error("SSE complete failed", e);
                            }
                        }
                );

        return emitter;
    }

    private Long parseConversationId(String id) {
        return id != null && !id.isBlank() ? Long.parseLong(id) : null;
    }

    private Conversation getOrCreateConversation(Long conversationId, Long userId, String firstMessage) {
        if (conversationId == null) {
            User user = userRepository.findById(java.util.Objects.requireNonNull(userId))
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            String title = firstMessage.length() > 50 ? firstMessage.substring(0, 50) : firstMessage;
            return conversationRepository.save(new Conversation(user, title));
        } else {
            return conversationRepository.findByIdAndUser_Id(conversationId, userId)
                    .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));
        }
    }

    private List<Message> getContextMessages(Long conversationId) {
        List<Message> messages = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId);
        return messages.size() > 20 ? messages.subList(messages.size() - 20, messages.size()) : messages;
    }

    private List<com.example.chatbot.dto.openai.Message> convertToOpenAiMessages(List<Message> messages) {
        List<com.example.chatbot.dto.openai.Message> result = new ArrayList<>();
        for (Message msg : messages) {
            result.add(new com.example.chatbot.dto.openai.Message(msg.getRole().name(), msg.getContent()));
        }
        return result;
    }
}
