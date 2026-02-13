package com.example.chatbot.service;

import com.example.chatbot.conversation.repository.ConversationRepository;
import com.example.chatbot.dto.openai.OpenAiMessage;
import com.example.chatbot.conversation.repository.MessageRepository;
import com.example.chatbot.entity.Conversation;
import com.example.chatbot.entity.Message;
import com.example.chatbot.entity.User;
import com.example.chatbot.global.error.AppException;
import com.example.chatbot.global.error.ErrorCode;
import com.example.chatbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationContextService {

    private static final int TITLE_MAX_LENGTH = 50;
    private static final int MIN_CONTEXT_SIZE = 1;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    @Value("${app.chat.context-size:10}")
    private int contextSize;

    @Transactional
    public Conversation getOrCreateConversation(Long conversationId, Long userId, String firstMessage) {
        if (conversationId == null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            String title = firstMessage.length() > TITLE_MAX_LENGTH
                    ? firstMessage.substring(0, TITLE_MAX_LENGTH)
                    : firstMessage;
            return conversationRepository.save(new Conversation(user, title));
        }

        return conversationRepository.findByIdAndUser_Id(conversationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    @Transactional
    public Message saveUserMessage(Conversation conversation, String content) {
        return messageRepository.save(new Message(conversation, Message.Role.user, content));
    }

    @Transactional
    public Message saveAssistantMessage(Conversation conversation, String content) {
        return messageRepository.save(new Message(conversation, Message.Role.assistant, content));
    }

    @Transactional(readOnly = true)
    public List<OpenAiMessage> buildOpenAiContextMessages(Long conversationId) {
        List<Message> messages = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId);
        int effectiveContextSize = Math.max(contextSize, MIN_CONTEXT_SIZE);
        List<Message> contextMessages = messages.size() > effectiveContextSize
                ? messages.subList(messages.size() - effectiveContextSize, messages.size())
                : messages;

        List<OpenAiMessage> openAiMessages = new ArrayList<>();
        for (Message message : contextMessages) {
            openAiMessages.add(new OpenAiMessage(message.getRole().name(), message.getContent()));
        }
        return openAiMessages;
    }
}
