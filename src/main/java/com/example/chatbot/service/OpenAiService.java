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
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // OpenAI Chat Completions API 호출 (assistant 응답 텍스트만 반환)
    public String createChatCompletion(List<Message> messages) {
        log.info("OpenAI API 호출: model={}, messages={}", openAiConfig.getModel(), messages.size());

        try {
            ChatRequest request = ChatRequest.builder()
                    .model(openAiConfig.getModel())
                    .messages(messages)
                    .stream(false)
                    .build();

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
                    .block();

            validateResponse(response);
            return response.getChoices().get(0).getMessage().getContent();

        } catch (WebClientResponseException e) {
            log.error("OpenAI API 호출 에러: code={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("OpenAI API 호출 실패: " + e.getMessage());
        } catch (Exception e) {
            log.error("예상치 못한 에러: ", e);
            throw new RuntimeException("OpenAI 서비스 에러: " + e.getMessage());
        }
    }

    // OpenAI 스트리밍 API 호출 (Flux<String> 반환)
    public reactor.core.publisher.Flux<String> createChatCompletionStream(List<Message> messages) {
        log.info("OpenAI API 스트리밍 호출: model={}, messages={}", openAiConfig.getModel(), messages.size());

        ChatRequest request = ChatRequest.builder()
                .model(openAiConfig.getModel())
                .messages(messages)
                .stream(true)
                .build();

        return openAiWebClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .flatMap(errorBody -> Mono.error(new RuntimeException("OpenAI API 에러: " + errorBody))))
                .bodyToFlux(String.class)
                .doOnNext(body -> log.debug("WebClient RAW BODY: '{}'", body))
                .flatMap(responseBody -> reactor.core.publisher.Flux.fromArray(responseBody.split("\n"))) // 줄 단위 분할
                .map(line -> {
                    // WebClient가 이미 파싱해서 data만 줄 수도 있고, Raw로 줄 수도 있음 유연하게 처리
                    if (line.startsWith("data:")) {
                        return line.substring(5).trim();
                    }
                    return line.trim();
                })
                .filter(data -> !data.isEmpty() && !"[DONE]".equals(data)) // [DONE] 및 빈 줄 필터링
                .map(data -> {
                    log.debug("SSE Data 파싱 시도: '{}'", data);
                    try {
                        com.example.chatbot.dto.openai.ChatCompletionChunkResponse chunk = 
                                objectMapper.readValue(data, com.example.chatbot.dto.openai.ChatCompletionChunkResponse.class);
                        if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                            String content = chunk.getChoices().get(0).getDelta().getContent();
                            return content != null ? content : "";
                        }
                        return "";
                    } catch (Exception e) {
                        log.error("JSON 파싱 에러: {}", data, e);
                        return "";
                    }
                })
                .filter(content -> !content.isEmpty());
    }

    private void validateResponse(ChatResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new RuntimeException("OpenAI API 응답이 비어있습니다.");
        }

        ChatResponse.Choice choice = response.getChoices().get(0);
        if (choice.getMessage() == null || choice.getMessage().getContent() == null) {
            throw new RuntimeException("OpenAI API 응답 메시지가 유효하지 않습니다.");
        }
    }
}
