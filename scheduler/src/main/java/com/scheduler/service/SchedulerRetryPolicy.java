package com.scheduler.service;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class SchedulerRetryPolicy {

  private static final int MAX_BACKOFF_SECONDS = 3600;

  public Instant computeNextFireTime(int attemptCount, int baseBackoffSeconds) {
    long delay = (long) baseBackoffSeconds * (long) Math.pow(2, attemptCount - 1);
    long jitter = ThreadLocalRandom.current().nextLong(delay + 1);
    long totalDelay = Math.min(delay + jitter, MAX_BACKOFF_SECONDS);

    return Instant.now().plusSeconds(totalDelay);
  }
}
