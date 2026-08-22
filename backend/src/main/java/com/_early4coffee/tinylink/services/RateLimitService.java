package com._early4coffee.tinylink.services;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j

public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    public boolean isAllowed(String clientIp) {
        return false;
    }
}
