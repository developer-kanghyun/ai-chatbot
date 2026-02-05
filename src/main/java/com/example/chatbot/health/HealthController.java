package com.example.chatbot.health;

import com.example.chatbot.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = Map.of(
            "status", "UP",
            "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()
        );
        return ApiResponse.success(data);
    }
}
