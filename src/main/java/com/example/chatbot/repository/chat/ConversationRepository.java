package com.example.chatbot.repository.chat;

import com.example.chatbot.domain.Conversation;
import java.util.Optional;

public interface ConversationRepository {
    Conversation save(Conversation conversation);
    Optional<Conversation> findById(String id);
}
