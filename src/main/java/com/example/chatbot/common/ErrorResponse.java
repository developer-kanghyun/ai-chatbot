package com.example.chatbot.common;

// 명세(docs/prompts/api.md)에 따른 공통 에러 응답 포맷
public class ErrorResponse {
    private final boolean success;
    private final ErrorDetail error;

    private ErrorResponse(String code, String message) {
        this.success = false;
        this.error = new ErrorDetail(code, message);
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public ErrorDetail getError() {
        return error;
    }

    public static class ErrorDetail {
        private final String code;
        private final String message;

        public ErrorDetail(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
