package com.scheduler.dto.response;

import com.scheduler.enums.HttpMethod;
import com.scheduler.enums.JobStatus;
import com.scheduler.enums.JobType;
import com.scheduler.enums.PrincipalType;
import java.time.Instant;
import java.util.UUID;

public record JobResponse(
    UUID id,
    String name,
    JobType type,
    String cronExpression,
    Instant nextFireTime,
    JobStatus status,
    String targetUrl,
    HttpMethod httpMethod,
    String payload,
    Integer maxAttempts,
    Integer attemptCount,
    Integer retryBackoffSeconds,
    PrincipalType ownerType,
    Long ownerId,
    Instant createdAt,
    Instant updatedAt) {}
