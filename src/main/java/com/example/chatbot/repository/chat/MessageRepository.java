package com.example.chatbot.repository.chat;

import com.example.chatbot.domain.Message;
import java.util.List;

public interface MessageRepository {
    Message save(Message message);
    List<Message> findByConversationId(String conversationId);
}
