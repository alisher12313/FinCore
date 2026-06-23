package com.pm.transactionservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimiterForTransactionService {

    private final RedisTemplate<String, Object> redisTemplate;

    public boolean allow(String userId, int limit, Duration windowSize){
        long windowIndex = System.currentTimeMillis() / windowSize.toMillis();
        String key = String.format("rate:%s:%s", userId, windowIndex);

        Long countHits = redisTemplate.opsForValue().increment(key);

        if(countHits != null && countHits == 1L){
            redisTemplate.expire(key, windowSize);
        }

        return countHits != null && countHits <= limit;
    }
}
