package com.scheduler.dto.response;

import com.scheduler.enums.RunStatus;
import java.time.Instant;
import java.util.UUID;

public record JobRunResponse(
    UUID id,
    UUID jobId,
    Integer attemptNumber,
    Instant startedAt,
    Instant endedAt,
    RunStatus status,
    Integer responseStatusCode,
    String errorMessage,
    Long durationMs) {}
