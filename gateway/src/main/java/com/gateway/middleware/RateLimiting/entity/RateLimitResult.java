package com.gateway.middleware.RateLimiting.entity;

import lombok.Getter;

@Getter
public class RateLimitResult {
  private final boolean allowed;
  private final Long remainingTokens;
  private final Long retryAfterSeconds;

  public RateLimitResult(boolean allowed, Long remainingTokens, Long retryAfterSeconds) {
    this.allowed = allowed;
    this.remainingTokens = remainingTokens;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
