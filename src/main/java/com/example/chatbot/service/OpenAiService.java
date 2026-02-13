package com.example.chatbot.service;

import com.example.chatbot.config.OpenAiConfig;
import com.example.chatbot.dto.openai.ChatCompletionChunkResponse;
import com.example.chatbot.dto.openai.ChatRequest;
import com.example.chatbot.dto.openai.ChatResponse;
import com.example.chatbot.dto.openai.OpenAiMessage;
import com.example.chatbot.global.error.AppException;
import com.example.chatbot.global.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService {

    private final WebClient openAiWebClient;
    private final OpenAiConfig openAiConfig;
    private final ObjectMapper objectMapper;

    public String createChatCompletion(List<OpenAiMessage> messages) {
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
                                    .flatMap(errorBody -> Mono.error(new AppException(
                                            ErrorCode.INTERNAL_SERVER_ERROR,
                                            "OpenAI API 클라이언트 에러: " + errorBody))))
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> Mono.error(new AppException(
                                            ErrorCode.INTERNAL_SERVER_ERROR,
                                            "OpenAI API 서버 에러: " + errorBody))))
                    .bodyToMono(ChatResponse.class)
                    .block();

            validateResponse(response);
            return response.getChoices().get(0).getMessage().getContent();

        } catch (WebClientResponseException e) {
            log.error("OpenAI API 호출 에러: code={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "OpenAI API 호출 실패: " + e.getMessage());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("예상치 못한 에러: ", e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "OpenAI 서비스 에러: " + e.getMessage());
        }
    }

    public Flux<String> createChatCompletionStream(List<OpenAiMessage> messages) {
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
                        .flatMap(errorBody -> Mono.error(new AppException(
                                ErrorCode.INTERNAL_SERVER_ERROR,
                                "OpenAI API 에러: " + errorBody))))
                .bodyToFlux(String.class)
                .doOnNext(body -> log.debug("WebClient RAW BODY: '{}'", body))
                .flatMap(responseBody -> Flux.fromArray(responseBody.split("\n")))
                .map(line -> {
                    if (line.startsWith("data:")) {
                        return line.substring(5).trim();
                    }
                    return line.trim();
                })
                .filter(data -> !data.isEmpty() && !"[DONE]".equals(data))
                .map(data -> {
                    log.debug("SSE Data 파싱 시도: '{}'", data);
                    try {
                        ChatCompletionChunkResponse chunk =
                                objectMapper.readValue(data, ChatCompletionChunkResponse.class);
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
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "OpenAI API 응답이 비어있습니다.");
        }

        ChatResponse.Choice choice = response.getChoices().get(0);
        if (choice.getMessage() == null || choice.getMessage().getContent() == null) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "OpenAI API 응답 메시지가 유효하지 않습니다.");
        }
    }
}
