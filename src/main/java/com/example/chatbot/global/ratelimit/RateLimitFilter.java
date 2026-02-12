package com.example.chatbot.global.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String identifier = "key:" + apiKey;
        log.info("RateLimit Check: uri={}, identifier={}", path, identifier);

        try {
            rateLimitService.checkRateLimit(identifier);
            filterChain.doFilter(request, response);
        } catch (RateLimitException e) {
            response.setStatus(429); // 429 Too Many Requests
            response.setHeader("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
            response.setContentType("application/json;charset=UTF-8");
            
            String json = String.format("{\"success\":false,\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"요청 횟수 제한 초과 (%d초 후 재시도)\"}}", 
                    e.getRetryAfterSeconds());
            response.getWriter().write(json);
        }
    }
}
