package com.scheduler.service;

import com.scheduler.entity.Job;
import com.scheduler.entity.JobRun;
import com.scheduler.enums.JobStatus;
import com.scheduler.enums.JobType;
import com.scheduler.enums.RunStatus;
import com.scheduler.repository.JobRepository;
import com.scheduler.repository.JobRunRepository;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobExecutionResultHandler {

  private final JobRepository jobRepository;
  private final JobRunRepository jobRunRepository;
  private final SchedulerRetryPolicy retryPolicy;

  @Transactional
  public void handleSuccess(Job job, int responseStatus, Instant startedAt) {
    long durationMs = Duration.between(startedAt, Instant.now()).toMillis();

    saveJobRun(job, RunStatus.SUCCESS, responseStatus, null, startedAt, durationMs);

    if (job.getType() == JobType.ONE_TIME) {
      job.setStatus(JobStatus.SUCCEEDED);
    } else {
      scheduleNextRecurringRun(job);
    }

    job.setUpdatedAt(Instant.now());
    jobRepository.save(job);

    log.info(
        "event=job_succeeded jobId={} durationMs={} responseStatus={}",
        job.getId(),
        durationMs,
        responseStatus);
  }

  @Transactional
  public void handleRetryableFailure(
      Job job, Integer responseStatus, String errorMessage, Instant startedAt) {
    long durationMs = Duration.between(startedAt, Instant.now()).toMillis();

    saveJobRun(job, RunStatus.FAILURE, responseStatus, errorMessage, startedAt, durationMs);

    if (job.getAttemptCount() < job.getMaxAttempts()) {
      Instant nextFireTime =
          retryPolicy.computeNextFireTime(job.getAttemptCount(), job.getRetryBackoffSeconds());
      job.setStatus(JobStatus.PENDING);
      job.setNextFireTime(nextFireTime);
      log.warn(
          "event=job_failed_retryable jobId={} attempt={} nextFireTime={}",
          job.getId(),
          job.getAttemptCount(),
          nextFireTime);
    } else if (job.getType() == JobType.RECURRING) {
      scheduleNextRecurringRun(job);
      log.error(
          "event=job_failed_terminal_occurrence jobId={} attempts={} nextFireTime={}",
          job.getId(),
          job.getAttemptCount(),
          job.getNextFireTime());
    } else {
      job.setStatus(JobStatus.FAILED);
      log.error(
          "event=job_failed_terminal jobId={} attempts={}", job.getId(), job.getAttemptCount());
    }

    job.setUpdatedAt(Instant.now());
    jobRepository.save(job);
  }

  @Transactional
  public void handleNonRetryableFailure(
      Job job, int responseStatus, String errorMessage, Instant startedAt) {
    long durationMs = Duration.between(startedAt, Instant.now()).toMillis();

    saveJobRun(job, RunStatus.FAILURE, responseStatus, errorMessage, startedAt, durationMs);

    job.setStatus(JobStatus.FAILED);
    job.setUpdatedAt(Instant.now());
    jobRepository.save(job);

    log.error(
        "event=job_failed_terminal jobId={} responseStatus={} reason=non-retryable",
        job.getId(),
        responseStatus);
  }

  private void scheduleNextRecurringRun(Job job) {
    CronExpression cron = CronExpression.parse(job.getCronExpression());
    Instant next = Objects.requireNonNull(cron.next(ZonedDateTime.now(ZoneOffset.UTC))).toInstant();
    job.setStatus(JobStatus.PENDING);
    job.setNextFireTime(next);
    job.setAttemptCount(0);
  }

  private void saveJobRun(
      Job job,
      RunStatus status,
      Integer responseStatus,
      String errorMessage,
      Instant startedAt,
      long durationMs) {
    JobRun run = new JobRun();
    run.setId(UUID.randomUUID());
    run.setJob(job);
    run.setAttemptNumber(job.getAttemptCount());
    run.setStartedAt(startedAt);
    run.setEndedAt(Instant.now());
    run.setStatus(status);
    run.setResponseStatusCode(responseStatus);
    run.setErrorMessage(errorMessage);
    run.setDurationMs(durationMs);
    jobRunRepository.save(run);
  }
}
