package com.example.chatbot.global.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisRateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 이미 ApiKeyAuthFilter에서 인증이 통과된 상태이므로, 헤더가 존재함이 보장됨
        String apiKey = request.getHeader("X-API-Key");
        String identifier = "key:" + apiKey;

        log.info("RateLimit Check: uri={}, identifier={}", request.getRequestURI(), identifier);
        rateLimitService.checkRateLimit(identifier);

        return true;
    }
}
