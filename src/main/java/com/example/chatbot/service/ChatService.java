package com.example.chatbot.service;

import com.example.chatbot.conversation.repository.ConversationRepository;
import com.example.chatbot.conversation.repository.MessageRepository;
import com.example.chatbot.dto.request.ChatCompletionRequest;
import com.example.chatbot.dto.response.ChatCompletionResponse;
import com.example.chatbot.entity.Conversation;
import com.example.chatbot.entity.Message;
import com.example.chatbot.entity.User;
import com.example.chatbot.global.error.AppException;
import com.example.chatbot.global.error.ErrorCode;
import com.example.chatbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final OpenAiService openAiService;

    // --- 1. Non-Streaming API ---
    @Transactional
    public ChatCompletionResponse createChatCompletion(ChatCompletionRequest request) {
        Long conversationId = parseConversationId(request.getConversationId());

        // 1. 대화 및 User 메시지 처리
        Conversation conversation = getOrCreateConversation(conversationId, 1L, request.getMessage());
        messageRepository.save(new Message(conversation, Message.Role.user, request.getMessage()));

        // 2. OpenAI 호출 준비 및 실행
        List<Message> contextMessages = getContextMessages(conversation.getId());
        List<com.example.chatbot.dto.openai.Message> openAiMessages = convertToOpenAiMessages(contextMessages);

        String assistantContent = openAiService.createChatCompletion(openAiMessages);
        
        // 3. Assistant 메시지 저장
        Message assistantMessage = messageRepository.save(new Message(conversation, Message.Role.assistant, assistantContent));

        return ChatCompletionResponse.builder()
                .conversationId(String.valueOf(conversation.getId()))
                .message(ChatCompletionResponse.MessageDto.builder()
                        .id(String.valueOf(assistantMessage.getId()))
                        .role(assistantMessage.getRole().name())
                        .content(assistantMessage.getContent())
                        .createdAt(assistantMessage.getCreatedAt())
                        .build())
                .build();
    }

    // --- 2. Streaming API (Reactive) ---
    public Flux<org.springframework.http.codec.ServerSentEvent<String>> createChatCompletionStream(ChatCompletionRequest request) {
        Long conversationId = parseConversationId(request.getConversationId());

        // 1. 초기 DB 작업 (Blocking이므로 래핑 필요, 여기선 편의상 동기 호출 유지 혹은 Mono.fromCallable 권장)
        // 트랜잭션 분리를 위해 실제로는 별도 메서드/클래스로 분리하는게 좋으나 일단 유지
        Conversation conversation = getOrCreateConversation(conversationId, 1L, request.getMessage());
        messageRepository.save(new Message(conversation, Message.Role.user, request.getMessage()));

        List<Message> contextMessages = getContextMessages(conversation.getId());
        List<com.example.chatbot.dto.openai.Message> openAiMessages = convertToOpenAiMessages(contextMessages);

        // 전체 응답 수집용 (Reactive 스트림 내 상태 관리)
        StringBuffer gatheredContent = new StringBuffer();

        return openAiService.createChatCompletionStream(openAiMessages)
                // 2. 각 Chunk를 SSE token 이벤트로 변환 (MASTER_PROMPT 요구사항 준수)
                .map(chunk -> {
                    gatheredContent.append(chunk); // 컨텐츠 수집
                    // JSON 이스케이프 필요 (간단히 처리, 실제론 ObjectMapper 권장되나 속도 위해 직접 조립)
                    String jsonChunk = "{\"text\":\"" + chunk.replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
                    return org.springframework.http.codec.ServerSentEvent.<String>builder()
                            .event("token")
                            .data(jsonChunk)
                            .build();
                })
                // 3. 스트림 끝에 done 이벤트 추가
                .concatWith(Mono.just(
                        org.springframework.http.codec.ServerSentEvent.<String>builder()
                                .event("done")
                                .data("{}")
                                .build()
                ))
                // 4. 완료 시 DB 저장 (비동기 Side Effect)
                .doOnComplete(() -> {
                    String fullContent = gatheredContent.toString();
                    if (!fullContent.isEmpty()) {
                        Mono.fromCallable(() -> messageRepository.save(new Message(conversation, Message.Role.assistant, fullContent)))
                                .subscribeOn(Schedulers.boundedElastic())
                                .subscribe(
                                        saved -> log.info("Assistant 메시지 저장 완료: msgId={}", saved.getId()),
                                        err -> log.error("SSE 완료 후 DB 저장 실패", err)
                                );
                    }
                })
                .doOnError(e -> log.error("SSE 스트리밍 중 에러 발생", e));
    }

    // --- Helper Methods ---

    private Long parseConversationId(String id) {
        return id != null && !id.isBlank() ? Long.parseLong(id) : null;
    }

    private Conversation getOrCreateConversation(Long conversationId, Long userId, String firstMessage) {
        if (conversationId == null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            String title = firstMessage.length() > 50 ? firstMessage.substring(0, 50) : firstMessage;
            return conversationRepository.save(new Conversation(user, title));
        } else {
            return conversationRepository.findByIdAndUser_Id(conversationId, userId)
                    .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));
        }
    }

    private List<Message> getContextMessages(Long conversationId) {
        List<Message> messages = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId);
        if (messages.size() > 20) {
            return messages.subList(messages.size() - 20, messages.size());
        }
        return messages;
    }

    private List<com.example.chatbot.dto.openai.Message> convertToOpenAiMessages(List<Message> messages) {
        List<com.example.chatbot.dto.openai.Message> result = new ArrayList<>();
        for (Message msg : messages) {
            result.add(new com.example.chatbot.dto.openai.Message(
                    msg.getRole().name(),
                    msg.getContent()
            ));
        }
        return result;
    }
}
