package com.scheduler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class SchedulerConfig {

  @Value("${scheduler.executor-core-pool-size:10}")
  private int corePoolSize;

  @Value("${scheduler.executor-max-pool-size:20}")
  private int maxPoolSize;

  @Bean(name = "jobExecutorPool", destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor jobExecutorPool() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("job-executor-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.initialize();
    return executor;
  }
}
