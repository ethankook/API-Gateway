package com.scheduler.UnitTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.dto.request.CreateJobRequest;
import com.scheduler.dto.response.CreateJobResponse;
import com.scheduler.entity.Job;
import com.scheduler.enums.HttpMethod;
import com.scheduler.enums.JobStatus;
import com.scheduler.enums.JobType;
import com.scheduler.enums.PrincipalType;
import com.scheduler.repository.JobRepository;
import com.scheduler.repository.JobRunRepository;
import com.scheduler.service.JobService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobServiceTests {

  @Mock private JobRepository jobRepository;

  @Mock private JobRunRepository jobRunRepository;

  @InjectMocks private JobService jobService;

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  private static List<InvalidCreateJobCase> invalidCreateJobRequests() {
    return List.of(
        new InvalidCreateJobCase("blank name", request -> withName(request, ""), "name"),
        new InvalidCreateJobCase("null type", request -> withType(request, null), "type"),
        new InvalidCreateJobCase(
            "one-time job without fireAt",
            request -> withFireAt(request, null),
            "fireAtValidForType"),
        new InvalidCreateJobCase(
            "invalid cron expression",
            request -> withType(withCronExpression(request, "not cron"), JobType.RECURRING),
            "cronExpression"),
        new InvalidCreateJobCase(
            "recurring job without cron expression",
            request -> withType(request, JobType.RECURRING),
            "cronExpressionPresentForRecurringJobs"),
        new InvalidCreateJobCase(
            "one-time job with cron expression",
            request -> withCronExpression(request, "0 0 * * * *"),
            "cronExpressionAbsentForOneTimeJobs"),
        new InvalidCreateJobCase(
            "blank target URL", request -> withTargetUrl(request, ""), "targetUrl"),
        new InvalidCreateJobCase(
            "invalid target URL",
            request -> withTargetUrl(request, "ftp://example.com"),
            "targetUrl"),
        new InvalidCreateJobCase(
            "null HTTP method", request -> withHttpMethod(request, null), "httpMethod"),
        new InvalidCreateJobCase(
            "zero max attempts", request -> withMaxAttempts(request, 0), "maxAttempts"),
        new InvalidCreateJobCase(
            "too many max attempts", request -> withMaxAttempts(request, 21), "maxAttempts"),
        new InvalidCreateJobCase(
            "zero retry backoff",
            request -> withRetryBackoffSeconds(request, 0),
            "retryBackoffSeconds"),
        new InvalidCreateJobCase(
            "too large retry backoff",
            request -> withRetryBackoffSeconds(request, 3601),
            "retryBackoffSeconds"));
  }

  @Test
  void claimDueJobs_shouldMarkAllDueJobsRunningAndIncrementAttemptCounts() {
    Job firstJob = new Job();
    firstJob.setId(UUID.randomUUID());
    firstJob.setStatus(JobStatus.PENDING);
    firstJob.setAttemptCount(0);

    Job secondJob = new Job();
    secondJob.setId(UUID.randomUUID());
    secondJob.setStatus(JobStatus.PENDING);
    secondJob.setAttemptCount(2);

    when(jobRepository.findDueJobsForUpdate(any(Instant.class), anyInt()))
        .thenReturn(List.of(firstJob, secondJob));

    List<Job> claimed = jobService.claimDueJobs();

    assertThat(claimed).containsExactly(firstJob, secondJob);
    assertThat(firstJob.getStatus()).isEqualTo(JobStatus.RUNNING);
    assertThat(firstJob.getAttemptCount()).isEqualTo(1);
    assertThat(secondJob.getStatus()).isEqualTo(JobStatus.RUNNING);
    assertThat(secondJob.getAttemptCount()).isEqualTo(3);
  }

  @Test
  void claimDueJobs_shouldReturnEmptyListWhenNoJobsAreDue() {
    when(jobRepository.findDueJobsForUpdate(any(Instant.class), anyInt())).thenReturn(List.of());

    List<Job> claimed = jobService.claimDueJobs();

    assertThat(claimed).isEmpty();
  }

  @Test
  void claimDueJobs_shouldQueryRepositoryWithCurrentTimeAndConfiguredBatchSize() {
    ReflectionTestUtils.setField(jobService, "batchSize", 7);
    Instant before = Instant.now();

    when(jobRepository.findDueJobsForUpdate(any(Instant.class), anyInt())).thenReturn(List.of());

    jobService.claimDueJobs();

    ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(jobRepository).findDueJobsForUpdate(nowCaptor.capture(), eq(7));
    assertThat(nowCaptor.getValue()).isBetween(before, Instant.now());
  }

  @Test
  void claimDueJobs_shouldNotCallSaveAll_whenNoJobsDue() {
    when(jobRepository.findDueJobsForUpdate(any(), anyInt())).thenReturn(Collections.emptyList());

    jobService.claimDueJobs();

    verify(jobRepository, never()).saveAll(any());
  }

  @Test
  void claimDueJobs_shouldSetUpdatedAt() {
    Job job = new Job();
    job.setId(UUID.randomUUID());
    job.setStatus(JobStatus.PENDING);
    job.setAttemptCount(0);
    Instant before = Instant.now();

    when(jobRepository.findDueJobsForUpdate(any(Instant.class), anyInt())).thenReturn(List.of(job));

    jobService.claimDueJobs();

    assertThat(job.getUpdatedAt()).isAfterOrEqualTo(before);
  }

  @Test
  void createJob_shouldSaveJobWithRequestAndOwnerFields() throws Exception {
    JsonNode payload = new ObjectMapper().readTree("{\"orderId\":123}");
    Instant fireAt = Instant.now().plusSeconds(3600);
    CreateJobRequest request =
        new CreateJobRequest(
            "Daily job",
            JobType.ONE_TIME,
            null,
            fireAt,
            "https://example.com/webhook",
            HttpMethod.POST,
            payload,
            3,
            60);
    UUID savedJobId = UUID.randomUUID();
    when(jobRepository.save(any(Job.class)))
        .thenAnswer(
            invocation -> {
              Job job = invocation.getArgument(0);
              job.setId(savedJobId);
              return job;
            });

    CreateJobResponse response = jobService.createJob(request, PrincipalType.USER, 1L);

    ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
    verify(jobRepository).save(jobCaptor.capture());
    Job savedJob = jobCaptor.getValue();
    assertThat(savedJob.getName()).isEqualTo("Daily job");
    assertThat(savedJob.getType()).isEqualTo(JobType.ONE_TIME);
    assertThat(savedJob.getCronExpression()).isNull();
    assertThat(savedJob.getNextFireTime()).isEqualTo(fireAt);
    assertThat(savedJob.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(savedJob.getTargetUrl()).isEqualTo("https://example.com/webhook");
    assertThat(savedJob.getHttpMethod()).isEqualTo(HttpMethod.POST);
    assertThat(savedJob.getPayload()).isEqualTo("{\"orderId\":123}");
    assertThat(savedJob.getMaxAttempts()).isEqualTo(3);
    assertThat(savedJob.getAttemptCount()).isZero();
    assertThat(savedJob.getRetryBackoffSeconds()).isEqualTo(60);
    assertThat(savedJob.getOwnerType()).isEqualTo(PrincipalType.USER);
    assertThat(savedJob.getOwnerId()).isEqualTo(1L);
    assertThat(response.id()).isEqualTo(savedJobId);
    assertThat(response.status()).isEqualTo(JobStatus.PENDING);
    assertThat(response.nextFireTime()).isEqualTo(fireAt);
  }

  @Test
  void createJob_shouldSaveNullPayloadAsNull() {
    CreateJobRequest request =
        new CreateJobRequest(
            "Daily job",
            JobType.ONE_TIME,
            null,
            Instant.now().plusSeconds(3600),
            "https://example.com/webhook",
            HttpMethod.POST,
            null,
            3,
            60);
    when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

    jobService.createJob(request, PrincipalType.USER, 1L);

    ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
    verify(jobRepository).save(jobCaptor.capture());
    assertThat(jobCaptor.getValue().getPayload()).isNull();
  }

  @Test
  void createJob_shouldUseProvidedFireAtForFirstRecurringRun() {
    Instant fireAt = Instant.now().plusSeconds(3600);
    CreateJobRequest request =
        new CreateJobRequest(
            "Recurring job",
            JobType.RECURRING,
            "0 0 * * * *",
            fireAt,
            "https://example.com/webhook",
            HttpMethod.POST,
            null,
            3,
            60);
    when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

    jobService.createJob(request, PrincipalType.USER, 1L);

    ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
    verify(jobRepository).save(jobCaptor.capture());
    assertThat(jobCaptor.getValue().getCronExpression()).isEqualTo("0 0 * * * *");
    assertThat(jobCaptor.getValue().getNextFireTime()).isEqualTo(fireAt);
  }

  @Test
  void createJob_shouldComputeInitialRecurringFireTimeWhenFireAtMissing() {
    CreateJobRequest request =
        new CreateJobRequest(
            "Recurring job",
            JobType.RECURRING,
            "0 0 * * * *",
            null,
            "https://example.com/webhook",
            HttpMethod.POST,
            null,
            3,
            60);
    when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

    jobService.createJob(request, PrincipalType.USER, 1L);

    ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
    verify(jobRepository).save(jobCaptor.capture());
    assertThat(jobCaptor.getValue().getNextFireTime()).isAfter(Instant.now());
  }

  @Test
  void createJob_shouldRejectNullRequest() {
    assertThatThrownBy(() -> jobService.createJob(null, PrincipalType.USER, 1L))
        .isInstanceOf(NullPointerException.class);

    verify(jobRepository, never()).save(any());
  }

  @Test
  void createJobRequestValidation_shouldRejectPastFireAt() {
    CreateJobRequest request =
        new CreateJobRequest(
            "Daily job",
            JobType.ONE_TIME,
            null,
            Instant.now().minusSeconds(3600),
            "https://example.com/webhook",
            HttpMethod.POST,
            null,
            3,
            60);
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    assertThat(validator.validate(request))
        .anySatisfy(violation -> assertThat(violation.getPropertyPath()).hasToString("fireAt"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidCreateJobRequests")
  void createJobRequestValidation_shouldRejectBadInputs(InvalidCreateJobCase invalidCase) {
    CreateJobRequest request = invalidCase.mutate().apply(validCreateJobRequest());

    assertThat(VALIDATOR.validate(request))
        .anySatisfy(
            violation ->
                assertThat(violation.getPropertyPath()).hasToString(invalidCase.propertyPath()));
  }

  private static CreateJobRequest validCreateJobRequest() {
    return new CreateJobRequest(
        "Daily job",
        JobType.ONE_TIME,
        null,
        Instant.now().plusSeconds(3600),
        "https://example.com/webhook",
        HttpMethod.POST,
        null,
        3,
        60);
  }

  private static CreateJobRequest withName(CreateJobRequest request, String name) {
    return new CreateJobRequest(
        name,
        request.type(),
        request.cronExpression(),
        request.fireAt(),
        request.targetUrl(),
        request.httpMethod(),
        request.payload(),
        request.maxAttempts(),
        request.retryBackoffSeconds());
  }

  private static CreateJobRequest withType(CreateJobRequest request, JobType type) {
    return new CreateJobRequest(
        request.name(),
        type,
        request.cronExpression(),
        request.fireAt(),
        request.targetUrl(),
        request.httpMethod(),
        request.payload(),
        request.maxAttempts(),
        request.retryBackoffSeconds());
  }

  private static CreateJobRequest withFireAt(CreateJobRequest request, Instant fireAt) {
    return new CreateJobRequest(
        request.name(),
        request.type(),
        request.cronExpression(),
        fireAt,
        request.targetUrl(),
        request.httpMethod(),
        request.payload(),
        request.maxAttempts(),
        request.retryBackoffSeconds());
  }

  private static CreateJobRequest withCronExpression(
      CreateJobRequest request, String cronExpression) {
    return new CreateJobRequest(
        request.name(),
        request.type(),
        cronExpression,
        request.fireAt(),
        request.targetUrl(),
        request.httpMethod(),
        request.payload(),
        request.maxAttempts(),
        request.retryBackoffSeconds());
  }

  private static CreateJobRequest withTargetUrl(CreateJobRequest request, String targetUrl) {
    return new CreateJobRequest(
        request.name(),
        request.type(),
        request.cronExpression(),
        request.fireAt(),
        targetUrl,
        request.httpMethod(),
        request.payload(),
        request.maxAttempts(),
        request.retryBackoffSeconds());
  }

  private static CreateJobRequest withHttpMethod(CreateJobRequest request, HttpMethod httpMethod) {
    return new CreateJobRequest(
        request.name(),
        request.type(),
        request.cronExpression(),
        request.fireAt(),
        request.targetUrl(),
        httpMethod,
        request.payload(),
        request.maxAttempts(),
        request.retryBackoffSeconds());
  }

  private static CreateJobRequest withMaxAttempts(CreateJobRequest request, Integer maxAttempts) {
    return new CreateJobRequest(
        request.name(),
        request.type(),
        request.cronExpression(),
        request.fireAt(),
        request.targetUrl(),
        request.httpMethod(),
        request.payload(),
        maxAttempts,
        request.retryBackoffSeconds());
  }

  private static CreateJobRequest withRetryBackoffSeconds(
      CreateJobRequest request, Integer retryBackoffSeconds) {
    return new CreateJobRequest(
        request.name(),
        request.type(),
        request.cronExpression(),
        request.fireAt(),
        request.targetUrl(),
        request.httpMethod(),
        request.payload(),
        request.maxAttempts(),
        retryBackoffSeconds);
  }

  private record InvalidCreateJobCase(
      String name, UnaryOperator<CreateJobRequest> mutate, String propertyPath) {}
}
