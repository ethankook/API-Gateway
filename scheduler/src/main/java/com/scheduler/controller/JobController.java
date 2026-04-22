package com.scheduler.controller;

import com.scheduler.dto.request.CreateJobRequest;
import com.scheduler.dto.response.CreateJobResponse;
import com.scheduler.dto.response.JobResponse;
import com.scheduler.dto.response.JobRunResponse;
import com.scheduler.enums.JobStatus;
import com.scheduler.enums.PrincipalType;
import com.scheduler.service.JobService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/jobs")
public class JobController {

  private final JobService jobService;

  @PostMapping("/")
  public ResponseEntity<CreateJobResponse> createJob(
      @RequestBody @Valid CreateJobRequest request, HttpServletRequest httpServletRequest) {
    PrincipalType ownerType =
        (PrincipalType) httpServletRequest.getAttribute("X-Authenticated-Principal-Type");
    Long ownerId = (Long) httpServletRequest.getAttribute("X-Authenticated-Principal-Id");

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(jobService.createJob(request, ownerType, ownerId));
  }

  @GetMapping("/{id}")
  public ResponseEntity<JobResponse> getJob(@PathVariable UUID id, HttpServletRequest request) {
    return ResponseEntity.ok(
        jobService.getJob(
            id,
            authenticatedPrincipalType(request),
            authenticatedPrincipalId(request),
            isAdmin(request)));
  }

  @GetMapping("/{id}/runs")
  public ResponseEntity<List<JobRunResponse>> getJobRuns(
      @PathVariable UUID id, HttpServletRequest request) {
    return ResponseEntity.ok(
        jobService.getJobRuns(
            id,
            authenticatedPrincipalType(request),
            authenticatedPrincipalId(request),
            isAdmin(request)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteJob(@PathVariable UUID id, HttpServletRequest request) {
    jobService.deleteJob(
        id,
        authenticatedPrincipalType(request),
        authenticatedPrincipalId(request),
        isAdmin(request));
    return ResponseEntity.noContent().build();
  }

  @GetMapping
  public ResponseEntity<List<JobResponse>> listJobs(
      @RequestParam(required = false) PrincipalType ownerType,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(required = false) JobStatus status,
      HttpServletRequest request) {
    return ResponseEntity.ok(
        jobService.listJobs(
            authenticatedPrincipalType(request),
            authenticatedPrincipalId(request),
            isAdmin(request),
            ownerType,
            ownerId,
            status));
  }

  private PrincipalType authenticatedPrincipalType(HttpServletRequest request) {
    return (PrincipalType) request.getAttribute("X-Authenticated-Principal-Type");
  }

  private Long authenticatedPrincipalId(HttpServletRequest request) {
    return (Long) request.getAttribute("X-Authenticated-Principal-Id");
  }

  private boolean isAdmin(HttpServletRequest request) {
    return Boolean.TRUE.equals(request.getAttribute("X-Authenticated-Is-Admin"));
  }
}
