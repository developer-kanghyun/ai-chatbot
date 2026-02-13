package com.example.chatbot.runner;

import com.example.chatbot.dto.openai.OpenAiMessage;
import com.example.chatbot.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Slf4j
@Component
@Profile({"local", "default"})
@RequiredArgsConstructor
public class OpenAiRunner implements CommandLineRunner {

    private final OpenAiService openAiService;

    @Override
    public void run(String... args) throws Exception {
        log.info("=== OpenAI 연동(Service) 테스트 시작 ===");

        try {
            List<OpenAiMessage> messages = List.of(
                    new OpenAiMessage("system", "You are a helpful assistant."),
                    new OpenAiMessage("user", "Hello! Who are you? Reply briefly.")
            );

            String response = openAiService.createChatCompletion(messages);
            log.info("GPT 응답: {}", response);
            log.info("=== OpenAI 연동 테스트 성공! ===");

        } catch (Exception e) {
            log.error("=== OpenAI 연동 테스트 실패 ===", e);
        }
    }
}
