package com.example.chatbot.global.ratelimit;

import lombok.Getter;

@Getter
public class RateLimitException extends RuntimeException {

    private final int limit;
    private final int windowSeconds;
    private final int retryAfterSeconds;

    public RateLimitException(int limit, int windowSeconds, int retryAfterSeconds) {
        super("Rate limit exceeded");
        this.limit = limit;
        this.windowSeconds = windowSeconds;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
