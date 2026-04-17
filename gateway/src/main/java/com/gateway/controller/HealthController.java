package com.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  @GetMapping("/health")
  @ResponseStatus(HttpStatus.OK)
  @ResponseBody
  public HealthResponse health() {
    return new HealthResponse("UP");
  }

  public record HealthResponse(String status) {}
}
