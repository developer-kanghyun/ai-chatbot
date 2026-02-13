package com.example.chatbot.conversation.repository;

import com.example.chatbot.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByUser_IdOrderByUpdatedAtDesc(Long userId);

    Optional<Conversation> findByIdAndUser_Id(Long id, Long userId);
}
