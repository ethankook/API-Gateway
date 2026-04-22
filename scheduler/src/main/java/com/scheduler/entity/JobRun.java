package com.scheduler.entity;

import com.scheduler.enums.RunStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_runs", schema = "scheduler")
@Getter
@Setter
@NoArgsConstructor
public class JobRun {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_id", nullable = false)
  private Job job;

  @Column(name = "attempt_number", nullable = false)
  private Integer attemptNumber;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private RunStatus status;

  @Column(name = "response_status_code")
  private Integer responseStatusCode;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "duration_ms")
  private Long durationMs;
}
