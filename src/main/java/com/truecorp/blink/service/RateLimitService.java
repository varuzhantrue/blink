package com.truecorp.blink.service;

import com.truecorp.blink.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final String LUA_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """;

    private static final DefaultRedisScript<Long> SCRIPT =
            new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitProperties rateLimitProperties;

    public boolean isAllowed(String username, String operation) {
        RateLimitProperties.Limit limit = switch (operation) {
            case "upload" -> rateLimitProperties.upload();
            case "download" -> rateLimitProperties.download();
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };

        String key = "rate:" + operation + ":" + username;
        Long count = redisTemplate.execute(SCRIPT, List.of(key), String.valueOf(limit.windowSeconds()));
        return count != null && count <= limit.requests();
    }
}
