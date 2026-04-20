package com.scheduler.repository;

import com.scheduler.entity.Job;
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
}
