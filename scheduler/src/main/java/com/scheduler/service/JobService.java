package com.scheduler.service;

import com.scheduler.dto.request.CreateJobRequest;
import com.scheduler.dto.response.CreateJobResponse;
import com.scheduler.entity.Job;
import com.scheduler.enums.JobStatus;
import com.scheduler.enums.PrincipalType;
import com.scheduler.repository.JobRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {
  private final JobRepository jobRepository;

  @Value("${scheduler.batch-size:10}")
  private int batchSize;

  public CreateJobResponse createJob(
      CreateJobRequest request, PrincipalType ownerType, Long ownerId) {
    log.info("Creating job from Request");
    Job job = new Job();
    job.setName(request.name());
    job.setType(request.type());
    job.setCronExpression(request.cronExpression());
    job.setNextFireTime(request.fireAt());
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

  @Transactional
  public List<Job> claimDueJobs() {
    log.info("Claiming {} due jobs", batchSize);
    List<Job> dueJobs = jobRepository.findDueJobsForUpdate(Instant.now(), batchSize);

    for (Job job : dueJobs) {
      job.setStatus(JobStatus.RUNNING);
      job.setAttemptCount(job.getAttemptCount() + 1);
      job.setUpdatedAt(Instant.now());
    }

    return dueJobs;
  }
}
