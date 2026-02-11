package com.example.chatbot.global.auth;

import com.example.chatbot.dto.common.ApiErrorResponse;
import com.example.chatbot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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


// API Key 인증 필터
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // /api/** 경로에 대해서만 인증 수행
        if (!requestUri.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader("X-API-Key");

        if (apiKey == null || apiKey.isBlank() || userRepository.findByApiKey(apiKey).isEmpty()) {
            log.warn("인증 실패: uri={}, apiKey={}", requestUri, apiKey);
            sendErrorResponse(response, apiKey == null || apiKey.isBlank());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, boolean isEmpty) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        String code = isEmpty ? "UNAUTHORIZED" : "INVALID_API_KEY";
        String message = isEmpty ? "인증이 필요한 서비스입니다." : "유효하지 않은 API Key입니다.";

        ApiErrorResponse errorResponse = ApiErrorResponse.error(code, message);
        String json = objectMapper.writeValueAsString(errorResponse);

        response.getWriter().write(json);
    }
}
