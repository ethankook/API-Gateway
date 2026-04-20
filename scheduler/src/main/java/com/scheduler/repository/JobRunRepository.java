package com.scheduler.repository;

import com.scheduler.entity.JobRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRunRepository extends JpaRepository<JobRun, UUID> {

  List<JobRun> findAllByJob_Id(UUID jobId);
}
