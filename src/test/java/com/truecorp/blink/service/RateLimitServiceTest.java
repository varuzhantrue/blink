package com.truecorp.blink.service;

import com.truecorp.blink.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RateLimitProperties rateLimitProperties;

    @InjectMocks
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        lenient().when(rateLimitProperties.upload())
                .thenReturn(new RateLimitProperties.Limit(10, 60));
        lenient().when(rateLimitProperties.download())
                .thenReturn(new RateLimitProperties.Limit(30, 60));
    }

    @Test
    @SuppressWarnings("unchecked")
    void isAllowed_ShouldReturnTrue_WhenUploadCountIsWithinLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(5L);

        assertTrue(rateLimitService.isAllowed("alice", "upload"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void isAllowed_ShouldReturnTrue_WhenUploadCountEqualsLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(10L);

        assertTrue(rateLimitService.isAllowed("alice", "upload"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void isAllowed_ShouldReturnFalse_WhenUploadCountExceedsLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(11L);

        assertFalse(rateLimitService.isAllowed("alice", "upload"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void isAllowed_ShouldReturnTrue_WhenDownloadCountIsWithinLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(15L);

        assertTrue(rateLimitService.isAllowed("alice", "download"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void isAllowed_ShouldReturnFalse_WhenDownloadCountExceedsLimit() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(31L);

        assertFalse(rateLimitService.isAllowed("alice", "download"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void isAllowed_ShouldReturnFalse_WhenRedisReturnsNull() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(null);

        assertFalse(rateLimitService.isAllowed("alice", "upload"));
    }

    @Test
    void isAllowed_ShouldThrowIllegalArgumentException_WhenOperationIsUnknown() {
        assertThrows(IllegalArgumentException.class,
                () -> rateLimitService.isAllowed("alice", "delete"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void isAllowed_ShouldUseCorrectRedisKey_ForUpload() {
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        when(redisTemplate.execute(any(RedisScript.class), keysCaptor.capture(), any())).thenReturn(1L);

        rateLimitService.isAllowed("bob", "upload");

        assertEquals(List.of("rate:upload:bob"), keysCaptor.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void isAllowed_ShouldUseCorrectRedisKey_ForDownload() {
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        when(redisTemplate.execute(any(RedisScript.class), keysCaptor.capture(), any())).thenReturn(1L);

        rateLimitService.isAllowed("bob", "download");

        assertEquals(List.of("rate:download:bob"), keysCaptor.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void isAllowed_ShouldPassWindowSecondsAsLuaArg() {
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), argsCaptor.capture())).thenReturn(1L);

        rateLimitService.isAllowed("carol", "upload");

        assertArrayEquals(new Object[]{"60"}, argsCaptor.getValue());
    }
}
