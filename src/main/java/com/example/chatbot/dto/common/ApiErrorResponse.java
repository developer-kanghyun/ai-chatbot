package com.example.chatbot.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {
    private boolean success;
    private ErrorDetail error;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private String code;
        private String message;
    }

    public static ApiErrorResponse error(String code, String message) {
        return ApiErrorResponse.builder()
                .success(false)
                .error(new ErrorDetail(code, message))
                .build();
    }
}
