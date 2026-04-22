package com.scheduler.UnitTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scheduler.entity.Job;
import com.scheduler.entity.JobRun;
import com.scheduler.enums.HttpMethod;
import com.scheduler.enums.JobStatus;
import com.scheduler.enums.JobType;
import com.scheduler.enums.PrincipalType;
import com.scheduler.enums.RunStatus;
import com.scheduler.repository.JobRepository;
import com.scheduler.repository.JobRunRepository;
import com.scheduler.service.JobExecutionResultHandler;
import com.scheduler.service.SchedulerRetryPolicy;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobExecutionResultHandlerTests {

  @Mock private JobRepository jobRepository;

  @Mock private JobRunRepository jobRunRepository;

  @Mock private SchedulerRetryPolicy retryPolicy;

  @InjectMocks private JobExecutionResultHandler resultHandler;

  @Test
  void handleSuccess_shouldMarkOneTimeJobSucceededAndRecordRun() {
    Job job = oneTimeJob();

    resultHandler.handleSuccess(job, 200, Instant.now().minusMillis(25));

    assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    JobRun run = savedRun();
    assertThat(run.getStatus()).isEqualTo(RunStatus.SUCCESS);
    assertThat(run.getResponseStatusCode()).isEqualTo(200);
    assertThat(run.getAttemptNumber()).isEqualTo(1);
    verify(jobRepository).save(job);
  }

  @Test
  void handleRetryableFailure_shouldReturnJobToPendingWhenAttemptsRemain() {
    Job job = oneTimeJob();
    Instant retryAt = Instant.now().plusSeconds(30);
    when(retryPolicy.computeNextFireTime(1, 10)).thenReturn(retryAt);

    resultHandler.handleRetryableFailure(job, 500, "server error", Instant.now().minusMillis(25));

    assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(job.getNextFireTime()).isEqualTo(retryAt);
    JobRun run = savedRun();
    assertThat(run.getStatus()).isEqualTo(RunStatus.FAILURE);
    assertThat(run.getResponseStatusCode()).isEqualTo(500);
  }

  @Test
  void handleRetryableFailure_shouldFailOneTimeJobWhenAttemptsExhausted() {
    Job job = oneTimeJob();
    job.setAttemptCount(3);
    job.setMaxAttempts(3);

    resultHandler.handleRetryableFailure(job, 500, "server error", Instant.now().minusMillis(25));

    assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(savedRun().getStatus()).isEqualTo(RunStatus.FAILURE);
  }

  @Test
  void handleRetryableFailure_shouldRescheduleRecurringJobWhenAttemptsExhausted() {
    Job job = recurringJob();
    job.setAttemptCount(3);
    job.setMaxAttempts(3);
    Instant before = Instant.now();

    resultHandler.handleRetryableFailure(job, 500, "server error", before.minusMillis(25));

    assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(job.getAttemptCount()).isZero();
    assertThat(job.getNextFireTime()).isAfter(before);
    assertThat(savedRun().getStatus()).isEqualTo(RunStatus.FAILURE);
  }

  @Test
  void handleNonRetryableFailure_shouldFailImmediately() {
    Job job = oneTimeJob();

    resultHandler.handleNonRetryableFailure(job, 404, "not found", Instant.now().minusMillis(25));

    assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
    JobRun run = savedRun();
    assertThat(run.getStatus()).isEqualTo(RunStatus.FAILURE);
    assertThat(run.getResponseStatusCode()).isEqualTo(404);
  }

  private JobRun savedRun() {
    ArgumentCaptor<JobRun> runCaptor = ArgumentCaptor.forClass(JobRun.class);
    verify(jobRunRepository).save(runCaptor.capture());
    return runCaptor.getValue();
  }

  private Job oneTimeJob() {
    Job job = baseJob();
    job.setType(JobType.ONE_TIME);
    return job;
  }

  private Job recurringJob() {
    Job job = baseJob();
    job.setType(JobType.RECURRING);
    job.setCronExpression("0 0 * * * *");
    return job;
  }

  private Job baseJob() {
    Job job = new Job();
    job.setId(UUID.randomUUID());
    job.setName("test-job");
    job.setStatus(JobStatus.RUNNING);
    job.setTargetUrl("https://example.com/webhook");
    job.setHttpMethod(HttpMethod.POST);
    job.setNextFireTime(Instant.now());
    job.setMaxAttempts(3);
    job.setAttemptCount(1);
    job.setRetryBackoffSeconds(10);
    job.setOwnerType(PrincipalType.USER);
    job.setOwnerId(1L);
    return job;
  }
}
