package com.scheduler.UnitTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

import com.scheduler.entity.Job;
import com.scheduler.enums.JobStatus;
import com.scheduler.repository.JobRepository;
import com.scheduler.service.JobExecutor;
import com.scheduler.service.JobService;
import com.scheduler.service.SchedulerEngine;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@ExtendWith(MockitoExtension.class)
class SchedulerEngineTests {

  @Mock private JobService jobService;

  @Mock private JobExecutor jobExecutor;

  @Mock private ThreadPoolTaskExecutor executorPool;

  @Mock private JobRepository jobRepository;

  @InjectMocks private SchedulerEngine schedulerEngine;

  @Test
  void poll_shouldDispatchEachClaimedJobToExecutorField() {

    Job job1 = jobWithId(UUID.randomUUID());
    Job job2 = jobWithId(UUID.randomUUID());

    when(jobService.claimDueJobs()).thenReturn(List.of(job1, job2));

    schedulerEngine.poll();

    verify(executorPool, times(2)).execute(any(Runnable.class));
  }

  @Test
  void poll_shouldDoNothingIfNoJobsDue() {
    when(jobService.claimDueJobs()).thenReturn(List.of());

    schedulerEngine.poll();

    verify(executorPool, never()).execute(any(Runnable.class));
  }

  @Test
  void poll_shouldContinueWhenExecutorPoolRejectsAJob() {
    Job job1 = jobWithId(UUID.randomUUID());
    Job job2 = jobWithId(UUID.randomUUID());

    when(jobService.claimDueJobs()).thenReturn(List.of(job1, job2));

    doThrow(new RejectedExecutionException("pool full"))
        .doNothing()
        .when(executorPool)
        .execute(any(Runnable.class));

    assertThatCode(() -> schedulerEngine.poll()).doesNotThrowAnyException();
    verify(jobService).resetRejectedDispatch(job1);
  }

  @Test
  void recoverStuckJobs_shouldResetStuckJobsToPending() {
    Job stuckJob = new Job();
    stuckJob.setId(UUID.randomUUID());
    stuckJob.setStatus(JobStatus.RUNNING);
    stuckJob.setUpdatedAt(Instant.now().minus(10, ChronoUnit.MINUTES));

    when(jobRepository.findStuckJobs(any(Instant.class))).thenReturn(List.of(stuckJob));

    schedulerEngine.recoverStuckJobs();

    assertThat(stuckJob.getStatus()).isEqualTo(JobStatus.PENDING);
    verify(jobRepository).saveAll(List.of(stuckJob));
  }

  @Test
  void recoverStuckJobs_shouldDoNothingWhenNoStuckJobs() {
    when(jobRepository.findStuckJobs(any())).thenReturn(Collections.emptyList());

    schedulerEngine.recoverStuckJobs();

    verify(jobRepository, never()).saveAll(any());
  }

  private Job jobWithId(UUID uuid) {
    Job job = new Job();
    job.setId(uuid);
    return job;
  }
}
