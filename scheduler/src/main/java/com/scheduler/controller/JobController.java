package com.scheduler.controller;

import com.scheduler.dto.request.CreateJobRequest;
import com.scheduler.dto.response.CreateJobResponse;
import com.scheduler.enums.PrincipalType;
import com.scheduler.service.JobService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    return ResponseEntity.ok(jobService.createJob(request, ownerType, ownerId));
  }
}
