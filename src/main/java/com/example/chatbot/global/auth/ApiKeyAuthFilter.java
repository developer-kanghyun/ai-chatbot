package com.example.chatbot.global.auth;

import com.example.chatbot.dto.common.ApiErrorResponse;
import com.example.chatbot.entity.User;
import com.example.chatbot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATED_USER_ID_ATTR = "authenticatedUserId";
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

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

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("인증 실패: uri={}, reason=missing_api_key", path);
            sendErrorResponse(response, true);
            return;
        }

        User user = userRepository.findByApiKey(apiKey).orElse(null);
        if (user == null) {
            log.warn("인증 실패: uri={}, reason=invalid_api_key, apiKeyPrefix={}", path, maskApiKey(apiKey));
            sendErrorResponse(response, false);
            return;
        }

        request.setAttribute(AUTHENTICATED_USER_ID_ATTR, user.getId());
        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, boolean isEmpty) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String code = isEmpty ? "UNAUTHORIZED" : "INVALID_API_KEY";
        String message = isEmpty ? "인증이 필요한 서비스입니다." : "유효하지 않은 API Key입니다.";

        ApiErrorResponse errorResponse = ApiErrorResponse.error(code, message);
        String json = objectMapper.writeValueAsString(errorResponse);

        response.getWriter().write(json);
    }

    private String maskApiKey(String apiKey) {
        int prefixLength = Math.min(4, apiKey.length());
        return apiKey.substring(0, prefixLength) + "****";
    }
}
