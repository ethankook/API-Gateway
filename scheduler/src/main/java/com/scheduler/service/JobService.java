package com.scheduler.service;

import com.scheduler.dto.request.CreateJobRequest;
import com.scheduler.dto.response.CreateJobResponse;
import com.scheduler.entity.Job;
import com.scheduler.enums.JobStatus;
import com.scheduler.enums.PrincipalType;
import com.scheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobService {
  private final JobRepository jobRepository;

  public CreateJobResponse createJob(
      CreateJobRequest request, PrincipalType ownerType, Long ownerId) {
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
}
