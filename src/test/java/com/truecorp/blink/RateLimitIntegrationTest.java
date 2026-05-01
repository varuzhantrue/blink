package com.truecorp.blink;

import com.truecorp.blink.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    private static final String TEST_USER = "test-subject";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RateLimitProperties rateLimitProperties;

    @BeforeEach
    void clearRateLimitKeys() {
        redisTemplate.delete("rate:upload:" + TEST_USER);
        redisTemplate.delete("rate:download:" + TEST_USER);
    }

    @Test
    @WithMockUser(username = TEST_USER)
    void uploadRateLimitIsEnforcedAfterLimitIsExceeded() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "integration-test.txt", "text/plain", "checking the rate limit".getBytes());

        int limit = rateLimitProperties.upload().requests();

        // First request must pass (verifies limit is not broken wide-open)
        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().is(not(equalTo(429))));

        // Exhaust the remaining slots
        for (int i = 1; i < limit; i++) {
            mockMvc.perform(multipart("/api/files/upload").file(file));
        }

        // One over the limit must be blocked by the rate limit filter
        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().is(429));
    }
}
