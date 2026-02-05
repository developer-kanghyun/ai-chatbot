package com.example.chatbot.exception;

// 명세(docs/prompts/api.md)에서 정의한 에러 코드 및 HTTP 상태 매핑
public enum ErrorCode {
    INVALID_REQUEST(400, "요청 유효성 실패(필드 누락, 형식 오류)"),
    UNAUTHORIZED(401, "인증 실패(API Key 미제공/불일치)"),
    NOT_FOUND(404, "리소스를 찾을 수 없습니다."),
    RATE_LIMIT_EXCEEDED(429, "Too many requests"),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String message;

    ErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return this.name();
    }

    public String getMessage() {
        return message;
    }
}
