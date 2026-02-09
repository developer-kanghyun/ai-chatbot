package com.example.chatbot.repository.chat.memory;

import com.example.chatbot.domain.Message;
import com.example.chatbot.repository.chat.MessageRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class MemoryMessageRepository implements MessageRepository {
    private final Map<String, Message> store = new ConcurrentHashMap<>();

    @Override
    public Message save(Message message) {
        store.put(message.getId(), message);
        return message;
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return store.values().stream()
                .filter(m -> m.getConversationId() != null && m.getConversationId().equals(conversationId))
                .sorted((m1, m2) -> {
                    if (m1.getCreatedAt() == null || m2.getCreatedAt() == null) return 0;
                    return m1.getCreatedAt().compareTo(m2.getCreatedAt());
                })
                .collect(Collectors.toList());
    }
}
