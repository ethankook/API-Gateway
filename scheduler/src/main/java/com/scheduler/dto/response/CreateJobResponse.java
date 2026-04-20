package com.scheduler.dto.response;

import com.scheduler.enums.JobStatus;
import java.time.Instant;
import java.util.UUID;

public record CreateJobResponse(UUID id, JobStatus status, Instant nextFireTime) {}
