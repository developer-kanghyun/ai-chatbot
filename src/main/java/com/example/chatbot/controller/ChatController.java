package com.example.chatbot.controller;

import com.example.chatbot.dto.common.ApiResponse;
import com.example.chatbot.dto.request.ChatCompletionRequest;
import com.example.chatbot.dto.response.ChatCompletionResponse;
import com.example.chatbot.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat API", description = "채팅 완료 API")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/completions")
    @Operation(summary = "채팅 완료", description = "사용자 메시지를 받아 AI 응답을 생성합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 오류", 
                    content = @Content(schema = @Schema(implementation = com.example.chatbot.dto.common.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "대화를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = com.example.chatbot.dto.common.ApiErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = com.example.chatbot.dto.common.ApiErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<ChatCompletionResponse>> createChatCompletion(
            @Valid @RequestBody ChatCompletionRequest request) {
        
        log.info("ChatCompletion 요청: message='{}', conversationId={}", 
                request.getMessage(), request.getConversationId());

        ChatCompletionResponse response = chatService.createChatCompletion(request);

        log.info("ChatCompletion 응답: conversationId={}, messageId={}", 
                response.getConversationId(), response.getMessage().getId());

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping(value = "/completions/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "채팅 스트리밍", description = "AI 응답을 SSE(Server-Sent Events)로 스트리밍합니다.")
    public reactor.core.publisher.Flux<org.springframework.http.codec.ServerSentEvent<String>> streamChatCompletion(
            @Valid @RequestBody ChatCompletionRequest request) {

        log.info("ChatCompletion(Stream) 요청: message='{}', conversationId={}",
                request.getMessage(), request.getConversationId());

        return chatService.createChatCompletionStream(request);
    }
}
