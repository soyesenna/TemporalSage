package com.senna.TemporalSage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "temporal.sage")
public class TemporalSageProperties {

  private String serviceAddress = "127.0.0.1:7233";
  private String namespace = "default";
  private String taskQueue = "SAGA_TASK_QUEUE";

  // Activity 설정
  private ActivityConfig activity = new ActivityConfig();

  // Worker 설정
  private WorkerConfig worker = new WorkerConfig();

  public static class ActivityConfig {

    private int startToCloseTimeoutMinutes = 5;
    private int scheduleToCloseTimeoutMinutes = 10;
    private int maxRetryAttempts = 3;

    // getters and setters

    public int getStartToCloseTimeoutMinutes() {
      return startToCloseTimeoutMinutes;
    }

    public void setStartToCloseTimeoutMinutes(int startToCloseTimeoutMinutes) {
      this.startToCloseTimeoutMinutes = startToCloseTimeoutMinutes;
    }

    public int getScheduleToCloseTimeoutMinutes() {
      return scheduleToCloseTimeoutMinutes;
    }

    public void setScheduleToCloseTimeoutMinutes(int scheduleToCloseTimeoutMinutes) {
      this.scheduleToCloseTimeoutMinutes = scheduleToCloseTimeoutMinutes;
    }

    public int getMaxRetryAttempts() {
      return maxRetryAttempts;
    }

    public void setMaxRetryAttempts(int maxRetryAttempts) {
      this.maxRetryAttempts = maxRetryAttempts;
    }
  }

  public static class WorkerConfig {

    private int maxConcurrentActivityExecutionSize = 100;
    private int maxConcurrentWorkflowTaskExecutionSize = 100;

    // getters and setters

    public int getMaxConcurrentActivityExecutionSize() {
      return maxConcurrentActivityExecutionSize;
    }

    public void setMaxConcurrentActivityExecutionSize(int maxConcurrentActivityExecutionSize) {
      this.maxConcurrentActivityExecutionSize = maxConcurrentActivityExecutionSize;
    }

    public int getMaxConcurrentWorkflowTaskExecutionSize() {
      return maxConcurrentWorkflowTaskExecutionSize;
    }

    public void setMaxConcurrentWorkflowTaskExecutionSize(
        int maxConcurrentWorkflowTaskExecutionSize) {
      this.maxConcurrentWorkflowTaskExecutionSize = maxConcurrentWorkflowTaskExecutionSize;
    }
  }

  // getters and setters

  public String getServiceAddress() {
    return serviceAddress;
  }

  public void setServiceAddress(String serviceAddress) {
    this.serviceAddress = serviceAddress;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getTaskQueue() {
    return taskQueue;
  }

  public void setTaskQueue(String taskQueue) {
    this.taskQueue = taskQueue;
  }

  public ActivityConfig getActivity() {
    return activity;
  }

  public void setActivity(ActivityConfig activity) {
    this.activity = activity;
  }

  public WorkerConfig getWorker() {
    return worker;
  }

  public void setWorker(WorkerConfig worker) {
    this.worker = worker;
  }
}