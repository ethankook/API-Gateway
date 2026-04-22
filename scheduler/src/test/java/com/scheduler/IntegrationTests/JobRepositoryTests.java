package com.scheduler.IntegrationTests;

import static org.assertj.core.api.Assertions.assertThat;

import com.scheduler.TestFixtures.TestHelpers;
import com.scheduler.entity.Job;
import com.scheduler.repository.JobRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestHelpers.class)
@Testcontainers
class JobRepositoryTests {

  @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private JobRepository jobRepository;

  @Autowired private TestHelpers testHelpers;

  @Autowired private TestEntityManager entityManager;

  @Autowired private PlatformTransactionManager transactionManager;

  @AfterEach
  void cleanUp() {
    jobRepository.deleteAll();
  }

  @Test
  void findDueJobsForUpdate_shouldReturnOnlyPendingJobsPastFireTime() {
    Job dueJob = testHelpers.createPendingJob(Instant.now().minus(1, ChronoUnit.MINUTES));
    Job futureJob = testHelpers.createPendingJob(Instant.now().plus(1, ChronoUnit.HOURS));
    Job runningJob = testHelpers.createRunningJob(Instant.now().minus(1, ChronoUnit.MINUTES));

    entityManager.persistAndFlush(dueJob);
    entityManager.persistAndFlush(futureJob);
    entityManager.persistAndFlush(runningJob);

    List<Job> foundJobs = jobRepository.findDueJobsForUpdate(Instant.now(), 10);

    assertThat(foundJobs).hasSize(1);
    assertThat(foundJobs.getFirst().getId()).isEqualTo(dueJob.getId());
  }

  @Test
  void findDueJobsForUpdate_shouldRespectBatchSizeLimit() {
    for (int i = 0; i < 10; i++) {
      entityManager.persistAndFlush(
          testHelpers.createPendingJob(Instant.now().minusSeconds(i + 1)));
    }

    List<Job> found = jobRepository.findDueJobsForUpdate(Instant.now(), 5);

    assertThat(found).hasSize(5);
  }

  @Test
  void findDueJobsForUpdate_shouldReturnJobsOrderedByNextFireTimeAscending() {
    Instant first = Instant.now().minus(3, ChronoUnit.HOURS);
    Instant second = Instant.now().minus(2, ChronoUnit.HOURS);
    Instant third = Instant.now().minus(1, ChronoUnit.HOURS);

    // insert out of order intentionally
    entityManager.persistAndFlush(testHelpers.createPendingJob(third));
    entityManager.persistAndFlush(testHelpers.createPendingJob(first));
    entityManager.persistAndFlush(testHelpers.createPendingJob(second));

    List<Job> found = jobRepository.findDueJobsForUpdate(Instant.now(), 10);

    assertThat(found.get(0).getNextFireTime()).isEqualTo(first);
    assertThat(found.get(1).getNextFireTime()).isEqualTo(second);
    assertThat(found.get(2).getNextFireTime()).isEqualTo(third);
  }

  @Test
  void findDueJobsForUpdate_shouldNotReturnSameJobToTwoConcurrentCallers()
      throws InterruptedException, ExecutionException {

    // create 20 due jobs
    for (int i = 0; i < 20; i++) {
      entityManager.persistAndFlush(testHelpers.createPendingJob(Instant.now().minusSeconds(1)));
    }
    entityManager.clear();
    TestTransaction.flagForCommit();
    TestTransaction.end();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch bothQueriesCompleted = new CountDownLatch(2);

    // simulate two scheduler instances querying simultaneously
    Future<List<UUID>> instance1 =
        pool.submit(() -> findDueJobIdsInTransaction(bothQueriesCompleted));

    Future<List<UUID>> instance2 =
        pool.submit(() -> findDueJobIdsInTransaction(bothQueriesCompleted));

    List<UUID> claimedByInstance1 = instance1.get();
    List<UUID> claimedByInstance2 = instance2.get();

    // the two lists should have no overlap
    List<UUID> allClaimed = new ArrayList<>(claimedByInstance1);
    allClaimed.addAll(claimedByInstance2);

    // no duplicates — every ID appears exactly once
    assertThat(allClaimed).doesNotHaveDuplicates();

    // all 20 jobs were claimed between the two instances
    assertThat(allClaimed).hasSize(20);

    pool.shutdown();
  }

  private List<UUID> findDueJobIdsInTransaction(CountDownLatch bothQueriesCompleted) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return transactionTemplate.execute(
        status -> {
          List<UUID> ids =
              jobRepository.findDueJobsForUpdate(Instant.now(), 20).stream()
                  .map(Job::getId)
                  .collect(Collectors.toList());
          bothQueriesCompleted.countDown();
          try {
            if (!bothQueriesCompleted.await(5, TimeUnit.SECONDS)) {
              throw new IllegalStateException("Timed out waiting for concurrent repository query");
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent query", e);
          }
          return ids;
        });
  }
}
