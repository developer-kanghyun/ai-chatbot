package com.example.chatbot.global.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** 윈도우 내 최대 허용 요청 수 */
    private int limit = 30;

    /** 고정 윈도우 크기 (초 단위) */
    private int windowSeconds = 60;

    /** Redis key prefix */
    private String keyPrefix = "rate_limit";
}
