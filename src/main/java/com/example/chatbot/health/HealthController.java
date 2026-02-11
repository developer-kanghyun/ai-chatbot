package com.example.chatbot.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@RestController
@Tag(name = "Health Check API", description = "서버 상태 확인을 위한 API")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "헬스체크", description = "서버가 정상 동작 중인지 확인합니다.")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString()
        ));
    }
}
