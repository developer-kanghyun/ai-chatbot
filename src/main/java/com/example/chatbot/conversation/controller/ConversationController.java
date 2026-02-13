package com.example.chatbot.conversation.controller;

import com.example.chatbot.conversation.dto.ConversationDetailResponse;
import com.example.chatbot.conversation.dto.ConversationListResponse;
import com.example.chatbot.conversation.dto.ConversationUpdateRequest;
import com.example.chatbot.conversation.dto.MessageResponse;
import com.example.chatbot.conversation.service.ConversationService;
import com.example.chatbot.dto.common.ApiResponse;
import com.example.chatbot.global.auth.AuthenticatedUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversation API", description = "대화 목록 및 상세 조회 API")
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    @Operation(summary = "대화 목록 조회", description = "사용자의 지난 대화 목록을 최신순으로 조회합니다.")
    public ResponseEntity<ApiResponse<List<ConversationListResponse>>> getConversations(@AuthenticatedUserId Long userId) {
        List<ConversationListResponse> response = conversationService.getConversations(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "대화 상세 조회", description = "특정 대화의 상세 정보와 메시지 히스토리를 조회합니다.")
    public ResponseEntity<ApiResponse<ConversationDetailResponse>> getConversation(@PathVariable Long id, @AuthenticatedUserId Long userId) {
        ConversationDetailResponse response = conversationService.getConversation(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "대화 메시지 조회", description = "특정 대화의 메시지 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessages(@PathVariable Long id, @AuthenticatedUserId Long userId) {
        List<MessageResponse> response = conversationService.getMessages(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "대화 삭제", description = "특정 대화를 삭제합니다. 하위 메시지도 함께 삭제됩니다.")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(@PathVariable Long id, @AuthenticatedUserId Long userId) {
        conversationService.deleteConversation(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "대화 제목 수정", description = "특정 대화의 제목을 수정합니다.")
    public ResponseEntity<ApiResponse<ConversationListResponse>> updateConversation(
            @PathVariable Long id,
            @AuthenticatedUserId Long userId,
            @Valid @RequestBody ConversationUpdateRequest request) {
        ConversationListResponse response = conversationService.updateConversationTitle(
                id, userId, request.getTitle());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
