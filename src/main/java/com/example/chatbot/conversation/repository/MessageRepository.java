package com.example.chatbot.conversation.repository;

import com.example.chatbot.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    
    // conversation_id로 메시지 조회 (created_at 오름차순)
    List<Message> findByConversation_IdOrderByCreatedAtAsc(Long conversationId);
    
    // conversation_id로 메시지 개수 조회
    long countByConversation_Id(Long conversationId);
}
