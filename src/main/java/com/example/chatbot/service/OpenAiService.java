package com.example.chatbot.service;

import com.example.chatbot.config.OpenAiConfig;
import com.example.chatbot.dto.openai.ChatRequest;
import com.example.chatbot.dto.openai.ChatResponse;
import com.example.chatbot.dto.openai.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.HttpStatusCode;
import java.util.List;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService {

    private final WebClient openAiWebClient;
    private final OpenAiConfig openAiConfig;

    /**
     * OpenAI API 호출 (동기 방식)
     * @param messages 대화 목록 (role, content)
     * @return Assistant 응답 텍스트
     */
    public String createChatCompletion(List<Message> messages) {
        log.info("OpenAI API 호출: model={}, messages={}", openAiConfig.getModel(), messages.size());

        try {
            ChatRequest request = ChatRequest.builder()
                    .model(openAiConfig.getModel())
                    .messages(messages)
                    .build();

            // WebClient 호출 (block()으로 동기 처리)
            ChatResponse response = openAiWebClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> Mono.error(new RuntimeException("OpenAI API 클라이언트 에러: " + errorBody))))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> Mono.error(new RuntimeException("OpenAI API 서버 에러: " + errorBody))))
                    .bodyToMono(ChatResponse.class)
                    .block(); // 동기 대기

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new RuntimeException("OpenAI API 응답이 비어있습니다.");
            }

            ChatResponse.Choice choice = response.getChoices().get(0);
            if (choice.getMessage() == null || choice.getMessage().getContent() == null) {
                throw new RuntimeException("OpenAI API 응답 메시지가 유효하지 않습니다.");
            }

            return choice.getMessage().getContent();

        } catch (WebClientResponseException e) {
            log.error("OpenAI API 호출 에러: code={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("OpenAI API 호출 실패: " + e.getMessage());
        } catch (Exception e) {
            log.error("예상치 못한 에러: ", e);
            throw new RuntimeException("OpenAI 서비스 에러: " + e.getMessage());
        }
    }
}
