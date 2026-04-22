package com.scheduler.repository;

import com.scheduler.entity.Job;
import com.scheduler.enums.JobStatus;
import com.scheduler.enums.PrincipalType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID> {

  @Query(
      value =
          """
            SELECT * FROM scheduler.jobs
            WHERE status = 'PENDING'
                AND next_fire_time <= :now
            ORDER BY next_fire_time
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """,
      nativeQuery = true)
  List<Job> findDueJobsForUpdate(@Param("now") Instant now, @Param("limit") int limit);

  @Query("SELECT j FROM Job j WHERE j.status = 'RUNNING' AND j.updatedAt < :threshold")
  List<Job> findStuckJobs(@Param("threshold") Instant threshold);

  @Query(
      """
      SELECT j FROM Job j
      WHERE (:ownerType IS NULL OR j.ownerType = :ownerType)
        AND (:ownerId IS NULL OR j.ownerId = :ownerId)
        AND (:status IS NULL OR j.status = :status)
      ORDER BY j.createdAt DESC
      """)
  List<Job> findJobs(
      @Param("ownerType") PrincipalType ownerType,
      @Param("ownerId") Long ownerId,
      @Param("status") JobStatus status);
}
