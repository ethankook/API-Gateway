package com.scheduler.service;

import com.scheduler.entity.Job;
import com.scheduler.enums.JobStatus;
import com.scheduler.repository.JobRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.jboss.logging.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SchedulerEngine {

  private final JobService jobService;
  private final JobExecutor jobExecutor;
  private final JobRepository jobRepository;
  private static final String INSTANCE_ID = UUID.randomUUID().toString();

  public SchedulerEngine(
      JobService jobService,
      JobExecutor jobExecutor,
      JobRepository jobRepository,
      ThreadPoolTaskExecutor jobExecutorPool) {
    this.jobService = jobService;
    this.jobExecutor = jobExecutor;
    this.jobRepository = jobRepository;
    this.executorPool = jobExecutorPool;
  }

  @Value("${scheduler.stuck-job-threshold-minutes:5}")
  private int stuckJobThresholdMinutes;

  @Qualifier("jobExecutorPool")
  private final ThreadPoolTaskExecutor executorPool;

  @Scheduled(
      fixedDelayString = "${scheduler.poll-interval-ms:100}",
      initialDelayString = "${scheduler.initial-delay-ms:0}")
  public void poll() {
    List<Job> jobs = jobService.claimDueJobs();

    if (jobs.isEmpty()) {
      return;
    }

    log.debug("Claiming {} jobs for execution", jobs.size());

    for (Job job : jobs) {
      try {
        executorPool.execute(() -> jobExecutor.execute(job));
      } catch (RejectedExecutionException e) {
        log.warn("Executor pool full, could not dispatch job {}", job.getId());
        jobService.resetRejectedDispatch(job);
      }
    }
  }

  @PostConstruct
  public void logStartup() {
    MDC.put("instanceId", INSTANCE_ID);
    log.info("Scheduler engine started, Instance ID = {}", INSTANCE_ID);
  }

  @PostConstruct
  public void recoverStuckJobs() {

    Instant threshold = Instant.now().minus(stuckJobThresholdMinutes, ChronoUnit.MINUTES);
    List<Job> stuckJobs = jobRepository.findStuckJobs(threshold);

    if (stuckJobs.isEmpty()) {
      return;
    }

    log.warn(
        "Found {} stuck jobs older than {} minutes, resetting to PENDING",
        stuckJobs.size(),
        stuckJobThresholdMinutes);

    for (Job job : stuckJobs) {
      log.warn("Resetting stuck job id = {} name = {}", job.getId(), job.getName());
      job.setStatus(JobStatus.PENDING);
      job.setUpdatedAt(Instant.now());
    }

    jobRepository.saveAll(stuckJobs);
  }
}
