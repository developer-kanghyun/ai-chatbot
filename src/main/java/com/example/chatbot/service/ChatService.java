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
        // 1. Conversation 확인/생성
        Long conversationId = request.getConversationId() != null && !request.getConversationId().isBlank()
                ? Long.parseLong(request.getConversationId())
                : null;
        Conversation conversation = getOrCreateConversation(conversationId, 1L, request.getMessage());

        // 2. User 메시지 저장
        Message userMessage = new Message(conversation, Message.Role.user, request.getMessage());
        messageRepository.save(userMessage);

        // 3. 기존 대화 컨텍스트 조회 (최근 20개)
        List<Message> contextMessages = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversation.getId());
        if (contextMessages.size() > 20) {
            contextMessages = contextMessages.subList(contextMessages.size() - 20, contextMessages.size());
        }

        // 4. OpenAI 호출용 메시지 리스트 구성
        List<com.example.chatbot.dto.openai.Message> openAiMessages = new ArrayList<>();
        for (Message msg : contextMessages) {
            openAiMessages.add(new com.example.chatbot.dto.openai.Message(
                    msg.getRole().name(),
                    msg.getContent()
            ));
        }

        // 5. OpenAI 호출 (String 반환)
        String assistantContent = openAiService.createChatCompletion(openAiMessages);

        // 6. Assistant 메시지 저장
        Message assistantMessage = new Message(conversation, Message.Role.assistant, assistantContent);
        messageRepository.save(assistantMessage);

        log.info("ChatCompletion 응답: conversationId={}, messageId={}", conversation.getId(), assistantMessage.getId());

        // 7. 응답 DTO 생성
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

    private Conversation getOrCreateConversation(Long conversationId, Long userId, String firstMessage) {
        if (conversationId == null) {
            // 신규 대화 생성
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            String title = firstMessage.length() > 50 ? firstMessage.substring(0, 50) : firstMessage;
            Conversation newConversation = new Conversation(user, title);
            return conversationRepository.save(newConversation);
        } else {
            // 기존 대화 조회
            return conversationRepository.findByIdAndUser_Id(conversationId, userId)
                    .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));
        }
    }
}
