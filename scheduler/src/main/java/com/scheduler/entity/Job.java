package com.scheduler.entity;

import com.scheduler.enums.HttpMethod;
import com.scheduler.enums.JobStatus;
import com.scheduler.enums.JobType;
import com.scheduler.enums.PrincipalType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "jobs", schema = "scheduler")
@Getter
@Setter
@NoArgsConstructor
public class Job {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "type", nullable = false)
  @Enumerated(EnumType.STRING)
  private JobType type;

  @Column(name = "cron_expression")
  private String cronExpression;

  @Column(name = "next_fire_time")
  private Instant nextFireTime;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private JobStatus status;

  @Column(name = "target_url", nullable = false)
  private String targetUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "http_method", nullable = false)
  private HttpMethod httpMethod;

  @Column(name = "payload")
  private String payload;

  @Column(name = "max_attempts", nullable = false)
  private Integer maxAttempts;

  @Column(name = "attempt_count", nullable = false)
  private Integer attemptCount;

  @Column(name = "retry_backoff_seconds", nullable = false)
  private Integer retryBackoffSeconds;

  @Enumerated(EnumType.STRING)
  @Column(name = "owner_type", nullable = false)
  private PrincipalType ownerType;

  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  public void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = Instant.now();
  }
}
