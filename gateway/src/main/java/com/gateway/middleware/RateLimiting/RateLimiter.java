package com.gateway.middleware.RateLimiting;

import com.gateway.config.RateLimitingProperties;
import com.gateway.middleware.RateLimiting.entities.RateLimitResult;
import com.gateway.middleware.RateLimiting.entities.TokenBucket;
import com.gateway.middleware.RouteMatching.Route;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class RateLimiter {

  private final RateLimitingProperties properties;

  private final ConcurrentHashMap<String, TokenBucket> tokenBuckets = new ConcurrentHashMap<>();

  public RateLimitResult isAllowed(String key, Route route) {
    long capacity =
        route.getRateLimitCapacity() != null
            ? route.getRateLimitCapacity()
            : properties.getDefaultCapacity();
    long refillRate =
        route.getRateLimitRefillRate() != null
            ? route.getRateLimitRefillRate()
            : properties.getDefaultRefillRate();
    TokenBucket tokenBucket =
        tokenBuckets.computeIfAbsent(
            key, ignored -> new TokenBucket(capacity, refillRate, capacity, Instant.now()));

    synchronized (tokenBucket) {
      refill(tokenBucket);

      if (tokenBucket.getTokens() <= 0) {
        return new RateLimitResult(false, 0L, 1L);
      }

      tokenBucket.setTokens(tokenBucket.getTokens() - 1);
      return new RateLimitResult(true, tokenBucket.getTokens(), 0L);
    }
  }

  private void refill(TokenBucket tokenBucket) {
    Instant now = Instant.now();
    long elapsedSeconds = Duration.between(tokenBucket.getLastRefillTime(), now).toSeconds();
    if (elapsedSeconds <= 0) {
      return;
    }

    long replenishedTokens = elapsedSeconds * tokenBucket.getRefillRate();
    long nextTokenCount =
        Math.min(tokenBucket.getCapacity(), tokenBucket.getTokens() + replenishedTokens);
    tokenBucket.setTokens(nextTokenCount);
    tokenBucket.setLastRefillTime(tokenBucket.getLastRefillTime().plusSeconds(elapsedSeconds));
  }

  @Scheduled(cron = "0 */10 * * * *")
  public void cleanupTokenBuckets() {
    tokenBuckets
        .entrySet()
        .removeIf(
            entry ->
                Duration.between(entry.getValue().getLastRefillTime(), Instant.now()).toMinutes()
                    > 60);
  }
}
