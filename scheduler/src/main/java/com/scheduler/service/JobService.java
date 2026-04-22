package com.scheduler.service;

import com.scheduler.dto.request.CreateJobRequest;
import com.scheduler.dto.response.CreateJobResponse;
import com.scheduler.dto.response.JobResponse;
import com.scheduler.dto.response.JobRunResponse;
import com.scheduler.entity.Job;
import com.scheduler.entity.JobRun;
import com.scheduler.enums.JobStatus;
import com.scheduler.enums.JobType;
import com.scheduler.enums.PrincipalType;
import com.scheduler.repository.JobRepository;
import com.scheduler.repository.JobRunRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {
  private final JobRepository jobRepository;
  private final JobRunRepository jobRunRepository;
  private final TargetUrlGuard targetUrlGuard;

  @Value("${scheduler.batch-size:10}")
  private int batchSize;

  @Transactional
  public CreateJobResponse createJob(
      CreateJobRequest request, PrincipalType ownerType, Long ownerId) {
    log.info("Creating job from Request");
    targetUrlGuard.validateAllowed(request.targetUrl());
    Job job = new Job();
    job.setName(request.name());
    job.setType(request.type());
    job.setCronExpression(request.type() == JobType.RECURRING ? request.cronExpression() : null);
    job.setNextFireTime(initialNextFireTime(request));
    job.setStatus(JobStatus.PENDING);
    job.setTargetUrl(request.targetUrl());
    job.setHttpMethod(request.httpMethod());
    job.setPayload(request.payload() == null ? null : request.payload().toString());
    job.setMaxAttempts(request.maxAttempts());
    job.setAttemptCount(0);
    job.setRetryBackoffSeconds(request.retryBackoffSeconds());
    job.setOwnerType(ownerType);
    job.setOwnerId(ownerId);

    Job savedJob = jobRepository.save(job);
    return new CreateJobResponse(
        savedJob.getId(), savedJob.getStatus(), savedJob.getNextFireTime());
  }

  public JobResponse getJob(UUID id) {
    return toJobResponse(getJobOrThrow(id));
  }

  public JobResponse getJob(
      UUID id, PrincipalType requesterType, Long requesterId, boolean requesterIsAdmin) {
    Job job = getJobOrThrow(id);
    authorizeJobAccess(job, requesterType, requesterId, requesterIsAdmin);
    return toJobResponse(job);
  }

  public List<JobResponse> listJobs(PrincipalType ownerType, Long ownerId, JobStatus status) {
    return jobRepository.findJobs(ownerType, ownerId, status).stream()
        .map(this::toJobResponse)
        .toList();
  }

  public List<JobResponse> listJobs(
      PrincipalType requesterType,
      Long requesterId,
      boolean requesterIsAdmin,
      PrincipalType ownerType,
      Long ownerId,
      JobStatus status) {
    PrincipalType effectiveOwnerType = ownerType;
    Long effectiveOwnerId = ownerId;

    if (!requesterIsAdmin) {
      if ((ownerType != null && ownerType != requesterType)
          || (ownerId != null && !Objects.equals(ownerId, requesterId))) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
      }

      effectiveOwnerType = requesterType;
      effectiveOwnerId = requesterId;
    }

    return listJobs(effectiveOwnerType, effectiveOwnerId, status);
  }

  public List<JobRunResponse> getJobRuns(UUID jobId) {
    getJobOrThrow(jobId);
    return jobRunRepository.findAllByJob_IdOrderByStartedAtAsc(jobId).stream()
        .map(this::toJobRunResponse)
        .toList();
  }

  public List<JobRunResponse> getJobRuns(
      UUID jobId, PrincipalType requesterType, Long requesterId, boolean requesterIsAdmin) {
    Job job = getJobOrThrow(jobId);
    authorizeJobAccess(job, requesterType, requesterId, requesterIsAdmin);
    return jobRunRepository.findAllByJob_IdOrderByStartedAtAsc(jobId).stream()
        .map(this::toJobRunResponse)
        .toList();
  }

  @Transactional
  public void deleteJob(UUID id) {
    Job job = getJobOrThrow(id);
    jobRepository.delete(job);
  }

  @Transactional
  public void deleteJob(
      UUID id, PrincipalType requesterType, Long requesterId, boolean requesterIsAdmin) {
    Job job = getJobOrThrow(id);
    authorizeJobAccess(job, requesterType, requesterId, requesterIsAdmin);
    jobRepository.delete(job);
  }

  @Transactional
  public List<Job> claimDueJobs() {
    List<Job> dueJobs = jobRepository.findDueJobsForUpdate(Instant.now(), batchSize);
    if (!dueJobs.isEmpty()) {
      log.debug("Claimed {} due jobs", dueJobs.size());
    }

    for (Job job : dueJobs) {
      job.setStatus(JobStatus.RUNNING);
      job.setAttemptCount(job.getAttemptCount() + 1);
      job.setUpdatedAt(Instant.now());
    }

    return dueJobs;
  }

  @Transactional
  public void resetRejectedDispatch(Job claimedJob) {
    Job job = getJobOrThrow(claimedJob.getId());
    job.setStatus(JobStatus.PENDING);
    job.setAttemptCount(Math.max(0, job.getAttemptCount() - 1));
    job.setNextFireTime(Instant.now().plusSeconds(1));
    job.setUpdatedAt(Instant.now());
  }

  private Instant initialNextFireTime(CreateJobRequest request) {
    if (request.fireAt() != null) {
      return request.fireAt();
    }

    CronExpression cron = CronExpression.parse(request.cronExpression());
    return Objects.requireNonNull(cron.next(ZonedDateTime.now(ZoneOffset.UTC))).toInstant();
  }

  private Job getJobOrThrow(UUID id) {
    return jobRepository
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
  }

  private void authorizeJobAccess(
      Job job, PrincipalType requesterType, Long requesterId, boolean requesterIsAdmin) {
    if (requesterIsAdmin) {
      return;
    }

    if (job.getOwnerType() != requesterType || !Objects.equals(job.getOwnerId(), requesterId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
    }
  }

  private JobResponse toJobResponse(Job job) {
    return new JobResponse(
        job.getId(),
        job.getName(),
        job.getType(),
        job.getCronExpression(),
        job.getNextFireTime(),
        job.getStatus(),
        job.getTargetUrl(),
        job.getHttpMethod(),
        job.getPayload(),
        job.getMaxAttempts(),
        job.getAttemptCount(),
        job.getRetryBackoffSeconds(),
        job.getOwnerType(),
        job.getOwnerId(),
        job.getCreatedAt(),
        job.getUpdatedAt());
  }

  private JobRunResponse toJobRunResponse(JobRun run) {
    return new JobRunResponse(
        run.getId(),
        run.getJob().getId(),
        run.getAttemptNumber(),
        run.getStartedAt(),
        run.getEndedAt(),
        run.getStatus(),
        run.getResponseStatusCode(),
        run.getErrorMessage(),
        run.getDurationMs());
  }
}
