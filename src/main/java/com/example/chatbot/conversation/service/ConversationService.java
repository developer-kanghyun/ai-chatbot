package com.example.chatbot.conversation.service;

import com.example.chatbot.conversation.dto.ConversationDetailResponse;
import com.example.chatbot.conversation.dto.ConversationListResponse;
import com.example.chatbot.conversation.dto.MessageResponse;
import com.example.chatbot.conversation.repository.ConversationRepository;
import com.example.chatbot.conversation.repository.MessageRepository;
import com.example.chatbot.entity.Conversation;
import com.example.chatbot.entity.Message;
import com.example.chatbot.global.error.AppException;
import com.example.chatbot.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    // 대화 목록 조회
    @Transactional(readOnly = true)
    public List<ConversationListResponse> getConversations() {
        Long userId = 1L;
        List<Conversation> conversations = conversationRepository.findByUser_IdOrderByUpdatedAtDesc(userId);
        log.info("대화 목록 조회: userId={}, count={}", userId, conversations.size());
        return ConversationListResponse.from(conversations);
    }

    // 대화 상세 조회
    @Transactional(readOnly = true)
    public ConversationDetailResponse getConversation(Long conversationId) {
        Long userId = 1L;

        Conversation conversation = conversationRepository
                .findByIdAndUser_Id(conversationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        List<Message> messages = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId);
        log.info("대화 상세 조회: conversationId={}, userId={}, messageCount={}", conversationId, userId, messages.size());

        return ConversationDetailResponse.from(conversation, messages);
    }

    // 메시지 목록 조회 API용
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(Long conversationId) {
        Long userId = 1L;

        if (conversationRepository.findByIdAndUser_Id(conversationId, userId).isEmpty()) {
            throw new AppException(ErrorCode.CONVERSATION_NOT_FOUND);
        }

        List<Message> messages = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId);

        return messages.stream()
                .map(MessageResponse::from)
                .toList();
    }
    // 대화 삭제
    @Transactional
    public void deleteConversation(Long conversationId) {
        Long userId = 1L;
        Conversation conversation = conversationRepository
                .findByIdAndUser_Id(conversationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        conversationRepository.delete(java.util.Objects.requireNonNull(conversation));
        log.info("대화 삭제 완료: conversationId={}, userId={}", conversationId, userId);
    }

    // 대화 제목 수정
    @Transactional
    public ConversationListResponse updateConversationTitle(Long conversationId, String title) {
        Long userId = 1L;
        Conversation conversation = conversationRepository
                .findByIdAndUser_Id(conversationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        conversation.setTitle(title);
        log.info("대화 제목 수정: conversationId={}, title={}", conversationId, title);
        return ConversationListResponse.from(List.of(conversation)).get(0);
    }
}
