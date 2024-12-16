package com.senna.TemporalSage.config;

import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import org.springframework.context.annotation.Configuration;

/**
 * SAGA Worker 설정을 관리하는 클래스
 */
@Configuration
public class SagaWorkerConfig {

  private static final String TASK_QUEUE = "SAGA_TASK_QUEUE";

  public static Worker createWorker(WorkerFactory factory) {
    WorkerOptions options = WorkerOptions.newBuilder()
        .setMaxConcurrentActivityExecutionSize(100)
        .setMaxConcurrentWorkflowTaskExecutionSize(100)
        .build();

    return factory.newWorker(TASK_QUEUE, options);
  }

  public static String getTaskQueue() {
    return TASK_QUEUE;
  }
} 