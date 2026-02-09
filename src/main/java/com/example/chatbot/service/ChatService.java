package com.example.chatbot.service;

import com.example.chatbot.domain.Conversation;
import com.example.chatbot.domain.Message;
import com.example.chatbot.domain.Role;
import com.example.chatbot.dto.request.ChatCompletionRequest;
import com.example.chatbot.dto.response.ChatCompletionResponse;
import com.example.chatbot.repository.chat.ConversationRepository;
import com.example.chatbot.repository.chat.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OpenAiService openAiService;

    public ChatCompletionResponse createChatCompletion(ChatCompletionRequest request) {
        // 1. Conversation 확인/생성
        Conversation conversation = getOrCreateConversation(request.getConversationId());

        // 2. 기존 대화 내역 조회 (히스토리)
        List<Message> history = messageRepository.findByConversationId(conversation.getId());

        // 3. User 메시지 저장
        Message userMessage = saveUserMessage(conversation.getId(), request.getMessage());

        // 4. OpenAI 호출용 메시지 리스트 구성
        List<com.example.chatbot.dto.openai.Message> openAiMessages = buildOpenAiMessages(history, userMessage);

        // 5. OpenAI 호출 (String만 반환)
        String assistantContent = openAiService.createChatCompletion(openAiMessages);

        // 6. Assistant 메시지 저장
        Message assistantMessage = saveAssistantMessage(conversation.getId(), assistantContent);

        // 7. 응답 DTO 생성
        return buildResponse(conversation.getId(), assistantMessage);
    }

    private Conversation getOrCreateConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            // 신규 대화 생성
            Instant now = Instant.now();
            Conversation newConversation = Conversation.builder()
                    .id(UUID.randomUUID().toString())
                    .createdAt(now)
                    .updatedAt(now)
                    .title(null) // Day2에서는 null 고정
                    .build();
            return conversationRepository.save(newConversation);
        } else {
            // 기존 대화 조회
            return conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다: " + conversationId));
        }
    }

    private Message saveUserMessage(String conversationId, String content) {
        Message userMessage = Message.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .role(Role.USER)
                .content(content)
                .createdAt(Instant.now())
                .build();
        return messageRepository.save(userMessage);
    }

    private Message saveAssistantMessage(String conversationId, String content) {
        Message assistantMessage = Message.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .role(Role.ASSISTANT)
                .content(content)
                .createdAt(Instant.now())
                .build();
        return messageRepository.save(assistantMessage);
    }

    private List<com.example.chatbot.dto.openai.Message> buildOpenAiMessages(List<Message> history, Message newUserMessage) {
        List<com.example.chatbot.dto.openai.Message> messages = new ArrayList<>();
        
        // 기존 히스토리 추가
        for (Message msg : history) {
            messages.add(new com.example.chatbot.dto.openai.Message(
                    msg.getRole().name().toLowerCase(), 
                    msg.getContent()
            ));
        }
        
        // 새 유저 메시지 추가
        messages.add(new com.example.chatbot.dto.openai.Message("user", newUserMessage.getContent()));
        
        return messages;
    }

    private ChatCompletionResponse buildResponse(String conversationId, Message assistantMessage) {
        return ChatCompletionResponse.builder()
                .conversationId(conversationId)
                .message(ChatCompletionResponse.MessageDto.builder()
                        .id(assistantMessage.getId())
                        .role(assistantMessage.getRole().name().toLowerCase())
                        .content(assistantMessage.getContent())
                        .createdAt(assistantMessage.getCreatedAt())
                        .build())
                .build();
    }
}
