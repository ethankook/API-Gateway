package com.scheduler.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.scheduler.enums.HttpMethod;
import com.scheduler.enums.JobType;
import jakarta.validation.constraints.*;
import java.time.Instant;
import validators.CronValidator.ValidCron;
import validators.UrlValidator.ValidUrl;

public record CreateJobRequest(
    @NotNull @Size(min = 1, max = 200) String name,
    @NotNull JobType type,
    @ValidCron String cronExpression,
    @Future Instant fireAt,
    @NotBlank @ValidUrl String targetUrl,
    @NotNull HttpMethod httpMethod,
    JsonNode payload,
    @NotNull @Min(1) @Max(20) Integer maxAttempts,
    @NotNull @Min(1) @Max(3600) Integer retryBackoffSeconds) {}
