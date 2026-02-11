package com.example.chatbot.global.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    /**
     * Rate Limit 검사.
     * 초과 시 RateLimitException을 던진다.
     *
     * Key 설계: {prefix}:{identifier}:{windowIndex}
     * windowIndex = currentEpochSec / windowSeconds → 윈도우가 바뀌면 새 키 생성
     */
    public void checkRateLimit(String identifier) {
        if (!properties.isEnabled()) return;

        long now = Instant.now().getEpochSecond();
        long windowIndex = now / properties.getWindowSeconds();
        String key = String.format("%s:%s:%d", properties.getKeyPrefix(), identifier, windowIndex);

        // Redis MULTI/EXEC 트랜잭션: INCR + EXPIRE 원자 실행
        List<Object> txResults = redisTemplate.execute(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<Object> execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForValue().increment(key);
                operations.expire(key, properties.getWindowSeconds() + 10, TimeUnit.SECONDS);
                return operations.exec();
            }
        });

        if (txResults == null || txResults.isEmpty()) {
            log.error("Redis 트랜잭션 실패: key={}", key);
            return; // Redis 장애 시 요청을 차단하지 않음 (fail-open)
        }

        // INCR 결과는 txResults의 첫 번째 요소
        long count = (Long) txResults.get(0);

        if (count > properties.getLimit()) {
            // 현재 윈도우의 남은 시간 계산
            long windowEnd = (windowIndex + 1) * properties.getWindowSeconds();
            int retryAfter = Math.max(1, (int) (windowEnd - now));

            log.warn("Rate limit 초과: key={}, count={}/{}, retryAfter={}s",
                    key, count, properties.getLimit(), retryAfter);
            throw new RateLimitException(properties.getLimit(), properties.getWindowSeconds(), retryAfter);
        }

        log.debug("Rate limit 통과: key={}, count={}/{}", key, count, properties.getLimit());
    }
}
