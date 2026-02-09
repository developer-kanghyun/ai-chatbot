package com.example.chatbot.repository.chat.memory;

import com.example.chatbot.domain.Conversation;
import com.example.chatbot.repository.chat.ConversationRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class MemoryConversationRepository implements ConversationRepository {
    private final Map<String, Conversation> store = new ConcurrentHashMap<>();

    @Override
    public Conversation save(Conversation conversation) {
        store.put(conversation.getId(), conversation);
        return conversation;
    }

    @Override
    public Optional<Conversation> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
}
