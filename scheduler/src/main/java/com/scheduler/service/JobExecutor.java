package com.scheduler.service;

import com.scheduler.entity.Job;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobExecutor {

  private final RestClient restClient;
  private final JobExecutionResultHandler resultHandler;

  public void execute(Job job) {
    Instant startedAt = Instant.now();

    try {
      ResponseEntity<Void> response = buildRequest(job).retrieve().toBodilessEntity();

      resultHandler.handleSuccess(job, response.getStatusCode().value(), startedAt);

    } catch (HttpClientErrorException e) {
      resultHandler.handleNonRetryableFailure(
          job, e.getStatusCode().value(), e.getMessage(), startedAt);
    } catch (HttpServerErrorException e) {
      resultHandler.handleRetryableFailure(
          job, e.getStatusCode().value(), e.getMessage(), startedAt);
    } catch (ResourceAccessException e) {
      resultHandler.handleRetryableFailure(job, null, e.getMessage(), startedAt);
    }
  }

  private RestClient.RequestHeadersSpec<?> buildRequest(Job job) {
    RestClient.RequestBodySpec spec =
        restClient
            .method(HttpMethod.valueOf(job.getHttpMethod().name()))
            .uri(job.getTargetUrl())
            .header("X-Scheduler-Job-Id", job.getId().toString())
            .header("X-Scheduler-Attempt", String.valueOf(job.getAttemptCount()))
            .header("X-Scheduler-Fire-Time", job.getNextFireTime().toString());

    if (job.getPayload() != null) {
      spec.body(job.getPayload());
    }

    return spec;
  }
}
