package com.scheduler.UnitTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.scheduler.entity.Job;
import com.scheduler.enums.HttpMethod;
import com.scheduler.enums.JobStatus;
import com.scheduler.enums.JobType;
import com.scheduler.enums.PrincipalType;
import com.scheduler.service.JobExecutionResultHandler;
import com.scheduler.service.JobExecutor;
import com.scheduler.service.TargetUrlGuard;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class JobExecutorTests {

  @Mock private JobExecutionResultHandler resultHandler;
  @Mock private TargetUrlGuard targetUrlGuard;

  private MockWebServer server;
  private JobExecutor executor;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    executor = new JobExecutor(RestClient.builder().build(), resultHandler, targetUrlGuard);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void execute_shouldSendSchedulerHeadersAndHandleSuccess() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200));
    Job job = job(server.url("/callback").toString());

    executor.execute(job);

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("X-Scheduler-Job-Id")).isEqualTo(job.getId().toString());
    assertThat(request.getHeader("X-Scheduler-Attempt")).isEqualTo("1");
    assertThat(request.getHeader("X-Scheduler-Fire-Time"))
        .isEqualTo(job.getNextFireTime().toString());
    assertThat(request.getBody().readUtf8()).isEqualTo("{\"ok\":true}");
    verify(resultHandler).handleSuccess(eq(job), eq(200), any(Instant.class));
  }

  @Test
  void execute_shouldClassifyServerErrorAsRetryable() {
    server.enqueue(new MockResponse().setResponseCode(500));
    Job job = job(server.url("/callback").toString());

    executor.execute(job);

    verify(resultHandler)
        .handleRetryableFailure(eq(job), eq(500), any(String.class), any(Instant.class));
  }

  @Test
  void execute_shouldClassifyClientErrorAsNonRetryable() {
    server.enqueue(new MockResponse().setResponseCode(404));
    Job job = job(server.url("/callback").toString());

    executor.execute(job);

    verify(resultHandler)
        .handleNonRetryableFailure(eq(job), eq(404), any(String.class), any(Instant.class));
  }

  @Test
  void execute_shouldClassifyConnectionFailureAsRetryable() throws IOException {
    String url = server.url("/callback").toString();
    server.shutdown();
    Job job = job(url);

    executor.execute(job);

    verify(resultHandler)
        .handleRetryableFailure(eq(job), eq(null), any(String.class), any(Instant.class));
  }

  @Test
  void execute_shouldClassifyBlockedTargetUrlAsNonRetryable() throws Exception {
    Job job = job(server.url("/callback").toString());
    doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target URL host is not allowed"))
        .when(targetUrlGuard)
        .validateAllowed(job.getTargetUrl());

    executor.execute(job);

    verify(resultHandler)
        .handleNonRetryableFailure(
            eq(job), eq(400), eq("Target URL host is not allowed"), any(Instant.class));
    assertThat(server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS)).isNull();
  }

  private Job job(String targetUrl) {
    Job job = new Job();
    job.setId(UUID.randomUUID());
    job.setName("test-job");
    job.setType(JobType.ONE_TIME);
    job.setStatus(JobStatus.RUNNING);
    job.setTargetUrl(targetUrl);
    job.setHttpMethod(HttpMethod.POST);
    job.setPayload("{\"ok\":true}");
    job.setNextFireTime(Instant.now().plusSeconds(30));
    job.setMaxAttempts(3);
    job.setAttemptCount(1);
    job.setRetryBackoffSeconds(10);
    job.setOwnerType(PrincipalType.USER);
    job.setOwnerId(1L);
    return job;
  }
}
