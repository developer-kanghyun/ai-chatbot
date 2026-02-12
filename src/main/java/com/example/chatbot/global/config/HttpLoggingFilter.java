package com.example.chatbot.global.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_PAYLOAD_LENGTH = 2048;
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "x-api-key", "authorization", "cookie", "set-cookie", "proxy-authorization"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);

        long startTime = System.currentTimeMillis();

        ContentCachingRequestWrapper requestWrapper = wrapRequest(request);
        boolean isSse = isSseRequest(request);
        
        HttpServletResponse responseToUse = isSse ? response : new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(requestWrapper, responseToUse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logRequestResponse(requestWrapper, responseToUse, duration);
            
            if (responseToUse instanceof ContentCachingResponseWrapper responseWrapper) {
                responseWrapper.copyBodyToResponse();
            }
            MDC.clear();
        }
    }

    private void logRequestResponse(ContentCachingRequestWrapper request, HttpServletResponse response, long duration) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        int status = response.getStatus();

        log.info("[{}] {} {} -> {} ({}ms) from={} ua={}",
                MDC.get("requestId"), method, path, status, duration, request.getRemoteAddr(), request.getHeader("User-Agent"));

        if (path != null && path.startsWith("/api/")) {
            log.debug("Request Headers: {}", getMaskedHeaders(request));
            log.debug("Request Body: {}", getPayload(request.getContentAsByteArray(), request.getCharacterEncoding()));

            if (response instanceof ContentCachingResponseWrapper responseWrapper) {
                log.debug("Response Body: {}", getPayload(responseWrapper.getContentAsByteArray(), responseWrapper.getCharacterEncoding()));
            } else {
                log.debug("Response Body: [STREAMING/SSE RESPONSE - LOGGING SKIPPED]");
            }
        }
    }

    private String getMaskedHeaders(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder("{");
        Collections.list(request.getHeaderNames()).forEach(headerName -> {
            String value = SENSITIVE_HEADERS.contains(headerName.toLowerCase()) ? "********" : request.getHeader(headerName);
            sb.append(headerName).append("=").append(value).append(", ");
        });
        if (sb.length() > 1) sb.setLength(sb.length() - 2);
        return sb.append("}").toString();
    }

    private String getPayload(byte[] content, String charEncoding) {
        if (content == null || content.length == 0) return "[empty]";
        try {
            int length = Math.min(content.length, MAX_PAYLOAD_LENGTH);
            String payload = new String(content, 0, length, charEncoding != null ? charEncoding : "UTF-8");
            return content.length > MAX_PAYLOAD_LENGTH ? payload + "... [truncated]" : payload;
        } catch (Exception e) {
            return "[unknown binary data]";
        }
    }

    private ContentCachingRequestWrapper wrapRequest(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper wrapper) return wrapper;
        // 의시적인 null 체크로 경고 해결
        if (request == null) return null; 
        return new ContentCachingRequestWrapper(request);
    }

    private boolean isSseRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return (path != null && path.endsWith("/stream")) || MediaType.TEXT_EVENT_STREAM_VALUE.equals(request.getHeader("Accept"));
    }
}
