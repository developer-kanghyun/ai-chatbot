package com.example.chatbot.conversation.controller;

import com.example.chatbot.conversation.dto.ConversationDetailResponse;
import com.example.chatbot.conversation.dto.ConversationListResponse;
import com.example.chatbot.conversation.dto.MessageResponse;
import com.example.chatbot.conversation.service.ConversationService;
import com.example.chatbot.dto.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversation API", description = "대화 목록 및 상세 조회 API")
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    @Operation(summary = "대화 목록 조회", description = "사용자의 지난 대화 목록을 최신순으로 조회합니다.")
    public ResponseEntity<ApiResponse<List<ConversationListResponse>>> getConversations() {
        List<ConversationListResponse> response = conversationService.getConversations();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "대화 상세 조회", description = "특정 대화의 상세 정보와 메시지 히스토리를 조회합니다.")
    public ResponseEntity<ApiResponse<ConversationDetailResponse>> getConversation(@PathVariable Long id) {
        ConversationDetailResponse response = conversationService.getConversation(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "대화 메시지 조회", description = "특정 대화의 메시지 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessages(@PathVariable Long id) {
        List<MessageResponse> response = conversationService.getMessages(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
