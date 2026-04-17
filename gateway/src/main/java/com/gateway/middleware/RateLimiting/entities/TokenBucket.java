package com.gateway.middleware.RateLimiting.entities;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class TokenBucket {

    public TokenBucket(Long capacity, Long refillRate, Long tokens, Instant lastRefillTime) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = tokens;
        this.lastRefillTime = lastRefillTime;
    }

    private Long capacity;
    private Long refillRate;
    private Long tokens;
    private Instant lastRefillTime;
}
