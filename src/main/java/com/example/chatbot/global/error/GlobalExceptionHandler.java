package com.example.chatbot.global.error;

import com.example.chatbot.dto.common.ApiErrorResponse;
import com.example.chatbot.global.ratelimit.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Rate Limiting 예외 (429 Too Many Requests)
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitException(RateLimitException ex) {
        log.warn("429 Too Many Requests: limit={}, retryAfter={}s", ex.getLimit(), ex.getRetryAfterSeconds());

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiErrorResponse.error("RATE_LIMIT_EXCEEDED",
                        String.format("요청 횟수 제한 초과 (제한: %d회/%d초, %d초 후 재시도)",
                                ex.getLimit(), ex.getWindowSeconds(), ex.getRetryAfterSeconds())));
    }

    // AppException 처리 (404, 400 등 모든 비즈니스 예외)
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiErrorResponse> handleAppException(AppException ex) {
        log.warn("AppException: code={}, message={}", ex.getErrorCode().getCode(), ex.getMessage());
        
        return ResponseEntity
                .status(ex.getErrorCode().getStatus())
                .body(ApiErrorResponse.error(ex.getErrorCode().getCode(), ex.getMessage()));
    }

    // Validation 에러 (400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        log.warn("Validation Error: {}", errorMessage);

        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.error("VALIDATION_ERROR", errorMessage));
    }

    // RuntimeException (500 - OpenAI 호출 실패 등)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntimeException(RuntimeException ex) {
        log.error("Server Error: ", ex);

        return ResponseEntity
                .internalServerError()
                .body(ApiErrorResponse.error("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }

    // 기타 예외 (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception ex) {
        log.error("Unexpected Error: ", ex);

        return ResponseEntity
                .internalServerError()
                .body(ApiErrorResponse.error("INTERNAL_SERVER_ERROR", "예상치 못한 오류가 발생했습니다."));
    }
}
