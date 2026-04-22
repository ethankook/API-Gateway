package com.scheduler.TestFixtures;

import com.scheduler.entity.Job;
import com.scheduler.enums.HttpMethod;
import com.scheduler.enums.JobStatus;
import com.scheduler.enums.JobType;
import com.scheduler.enums.PrincipalType;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class TestHelpers {

  public Job createPendingJob(Instant nextFireTime) {
    Job job = new Job();
    job.setName("test-job");
    job.setStatus(JobStatus.PENDING);
    job.setType(JobType.ONE_TIME);
    job.setTargetUrl("http://example.com");
    job.setHttpMethod(HttpMethod.POST);
    job.setNextFireTime(nextFireTime);
    job.setMaxAttempts(3);
    job.setAttemptCount(0);
    job.setRetryBackoffSeconds(30);
    job.setOwnerType(PrincipalType.USER);
    job.setOwnerId(1L);
    job.setCreatedAt(Instant.now());
    job.setUpdatedAt(Instant.now());
    return job;
  }

  public Job createRunningJob(Instant nextFireTime) {
    Job job = new Job();
    job.setName("test-job");
    job.setStatus(JobStatus.RUNNING);
    job.setType(JobType.ONE_TIME);
    job.setTargetUrl("http://example.com");
    job.setHttpMethod(HttpMethod.POST);
    job.setNextFireTime(nextFireTime);
    job.setMaxAttempts(3);
    job.setAttemptCount(0);
    job.setRetryBackoffSeconds(30);
    job.setOwnerType(PrincipalType.USER);
    job.setOwnerId(1L);
    job.setCreatedAt(Instant.now());
    job.setUpdatedAt(Instant.now());
    return job;
  }
}
