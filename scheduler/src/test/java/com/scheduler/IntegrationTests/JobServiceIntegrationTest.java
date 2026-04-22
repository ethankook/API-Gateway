package com.scheduler.IntegrationTests;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.scheduler.TestFixtures.BaseIntegrationTest;
import com.scheduler.TestFixtures.TestHelpers;
import com.scheduler.entity.Job;
import com.scheduler.enums.JobStatus;
import com.scheduler.repository.JobRepository;
import com.scheduler.service.JobService;
import com.scheduler.service.SchedulerEngine;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
public class JobServiceIntegrationTest extends BaseIntegrationTest {

  @Autowired private JobService jobService;

  @Autowired private TestHelpers testHelpers;

  @Autowired private JobRepository jobRepository;

  @Autowired private SchedulerEngine schedulerEngine;

  @Autowired private EntityManager entityManager;

  @AfterEach
  void cleanUp() {
    jobRepository.deleteAll();
  }

  @Test
  void claimDueJobs_shouldPersistRunningStatusToDatabase() {

    Job job = testHelpers.createPendingJob(Instant.now().minusSeconds(30));
    jobRepository.save(job);

    jobService.claimDueJobs();

    Job j = jobRepository.findById(job.getId()).orElseThrow();

    assertThat(job.getId().equals(j.getId()));
    assertThat(j.getStatus()).isEqualTo(JobStatus.RUNNING);
    assertThat(j.getAttemptCount()).isEqualTo(1);
  }

  @Test
  @Transactional
  void recoverStuckJobs_shouldResetOldRunningJobsToPending() {
    Job stuckJob = testHelpers.createRunningJob(Instant.now().minus(10, ChronoUnit.MINUTES));
    jobRepository.saveAndFlush(stuckJob);

    Job recentJob = testHelpers.createRunningJob(Instant.now().minus(1, ChronoUnit.MINUTES));
    jobRepository.saveAndFlush(recentJob);

    updateJobUpdatedAt(stuckJob, Instant.now().minus(10, ChronoUnit.MINUTES));
    updateJobUpdatedAt(recentJob, Instant.now().minus(1, ChronoUnit.MINUTES));
    entityManager.clear();

    schedulerEngine.recoverStuckJobs();

    Job reloadedStuck = jobRepository.findById(stuckJob.getId()).orElseThrow();
    Job reloadedRecent = jobRepository.findById(recentJob.getId()).orElseThrow();

    assertThat(reloadedStuck.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(reloadedRecent.getStatus()).isEqualTo(JobStatus.RUNNING);
  }

  private void updateJobUpdatedAt(Job job, Instant updatedAt) {
    entityManager
        .createQuery("UPDATE Job j SET j.updatedAt = :updatedAt WHERE j.id = :id")
        .setParameter("updatedAt", updatedAt)
        .setParameter("id", job.getId())
        .executeUpdate();
  }
}
