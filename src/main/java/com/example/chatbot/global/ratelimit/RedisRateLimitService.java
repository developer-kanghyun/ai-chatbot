package com.example.chatbot.global.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisRateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    public void checkRateLimit(String identifier) {
        if (!properties.isEnabled()) return;

        long now = Instant.now().getEpochSecond();
        long windowIndex = now / properties.getWindowSeconds();
        String key = String.format("%s:%s:%d", properties.getKeyPrefix(), identifier, windowIndex);

        List<Object> txResults = redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            public <K, V> List<Object> execute(@NonNull RedisOperations<K, V> operations) throws DataAccessException {
                @SuppressWarnings("unchecked")
                RedisOperations<String, String> stringOps = (RedisOperations<String, String>) operations;
                stringOps.multi();
                stringOps.opsForValue().increment(key);
                stringOps.expire(key, properties.getWindowSeconds() + 10, TimeUnit.SECONDS);
                return stringOps.exec();
            }
        });

        if (txResults == null || txResults.isEmpty()) {
            log.error("Redis 트랜잭션 실패: key={}", key);
            return;
        }

        long count = (Long) txResults.get(0);

        if (count > properties.getLimit()) {
            long windowEnd = (windowIndex + 1) * properties.getWindowSeconds();
            int retryAfter = Math.max(1, (int) (windowEnd - now));
            throw new RateLimitException(properties.getLimit(), properties.getWindowSeconds(), retryAfter);
        }
    }
}
