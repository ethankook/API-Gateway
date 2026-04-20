package com.gateway.middleware.RateLimiting.entity;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

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
