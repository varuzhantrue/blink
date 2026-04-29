package com.truecorp.blink.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("blink.rate-limit")
public record RateLimitProperties(Limit upload, Limit download) {

    public record Limit(int requests, int windowSeconds) {}
}
