package com.example.chatbot.conversation.repository;

import com.example.chatbot.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    
    // user_id로 대화 목록 조회 (updated_at 내림차순)
    List<Conversation> findByUser_IdOrderByUpdatedAtDesc(Long userId);
    
    // user_id와 conversation_id로 조회 (권한 확인용)
    Optional<Conversation> findByIdAndUser_Id(Long id, Long userId);
}
