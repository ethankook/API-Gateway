package com.gateway.middleware.RateLimiting;

import static org.assertj.core.api.Assertions.assertThat;

import com.gateway.config.RateLimitingProperties;
import com.gateway.middleware.RateLimiting.entity.RateLimitResult;
import com.gateway.middleware.RateLimiting.entity.TokenBucket;
import com.gateway.middleware.RouteMatching.Route;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RateLimiterTests {

  private static final long DEFAULT_CAPACITY = 10L;
  private static final long DEFAULT_REFILL_RATE = 5L;

  private RateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    RateLimitingProperties properties = new RateLimitingProperties();
    properties.setDefaultCapacity(DEFAULT_CAPACITY);
    properties.setDefaultRefillRate(DEFAULT_REFILL_RATE);
    rateLimiter = new RateLimiter(properties);
  }

  @Test
  void nullCapacityUsesDefaultCapacity() throws Exception {
    rateLimiter.isAllowed("key1", routeWithLimits(null, 2L));
    assertThat(getBuckets().get("key1").getCapacity()).isEqualTo(DEFAULT_CAPACITY);
  }

  @Test
  void nullRefillRateUsesDefaultRefillRate() throws Exception {
    rateLimiter.isAllowed("key2", routeWithLimits(10L, null));
    assertThat(getBuckets().get("key2").getRefillRate()).isEqualTo(DEFAULT_REFILL_RATE);
  }

  @Test
  void emptyBucketReturnsDeniedWithRetryAfter() throws Exception {
    getBuckets().put("key3", new TokenBucket(10L, 1L, 0L, Instant.now()));
    RateLimitResult result = rateLimiter.isAllowed("key3", routeWithLimits(10L, 1L));
    assertThat(result.isAllowed()).isFalse();
    assertThat(result.getRetryAfterSeconds()).isEqualTo(1L);
  }

  @Test
  void firstRequestConsumesOneTokenFromNewBucket() {
    RateLimitResult result = rateLimiter.isAllowed("fresh-key", routeWithLimits(4L, 1L));
    assertThat(result.isAllowed()).isTrue();
    assertThat(result.getRemainingTokens()).isEqualTo(3L);
    assertThat(result.getRetryAfterSeconds()).isEqualTo(0L);
  }

  @Test
  void bucketRefillsAtConfiguredRatePerSecond() throws Exception {
    getBuckets().put("key4", new TokenBucket(20L, 3L, 0L, Instant.now().minusSeconds(3)));
    RateLimitResult result = rateLimiter.isAllowed("key4", routeWithLimits(20L, 3L));
    assertThat(result.isAllowed()).isTrue();
    assertThat(result.getRemainingTokens()).isEqualTo(8L);
  }

  @Test
  void tokensNeverExceedCapacity() throws Exception {
    getBuckets().put("key5", new TokenBucket(5L, 10L, 3L, Instant.now().minusSeconds(10)));
    RateLimitResult result = rateLimiter.isAllowed("key5", routeWithLimits(5L, 10L));
    assertThat(result.isAllowed()).isTrue();
    assertThat(result.getRemainingTokens()).isEqualTo(4L);
  }

  @Test
  void existingBucketKeepsOriginalLimitsForSameKey() throws Exception {
    rateLimiter.isAllowed("shared-key", routeWithLimits(4L, 1L));
    rateLimiter.isAllowed("shared-key", routeWithLimits(99L, 99L));

    TokenBucket bucket = getBuckets().get("shared-key");
    assertThat(bucket.getCapacity()).isEqualTo(4L);
    assertThat(bucket.getRefillRate()).isEqualTo(1L);
  }

  @Test
  void cleanupRemovesBucketsInactiveForMoreThanSixtyMinutes() throws Exception {
    getBuckets().put("stale", new TokenBucket(10L, 1L, 5L, Instant.now().minusSeconds(3661)));
    getBuckets().put("fresh", new TokenBucket(10L, 1L, 5L, Instant.now().minusSeconds(60)));

    rateLimiter.cleanupTokenBuckets();

    assertThat(getBuckets()).doesNotContainKey("stale");
    assertThat(getBuckets()).containsKey("fresh");
  }

  private Route routeWithLimits(Long capacity, Long refillRate) {
    Route route = new Route();
    route.setRouteId("test-route");
    route.setRateLimitCapacity(capacity);
    route.setRateLimitRefillRate(refillRate);
    return route;
  }

  @SuppressWarnings("unchecked")
  private ConcurrentHashMap<String, TokenBucket> getBuckets() throws Exception {
    Field field = RateLimiter.class.getDeclaredField("tokenBuckets");
    field.setAccessible(true);
    return (ConcurrentHashMap<String, TokenBucket>) field.get(rateLimiter);
  }
}
