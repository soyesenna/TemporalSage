package com.senna.TemporalSage.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "temporal.sage")
@Component
@Primary
public class TemporalSageProperties {

  private String serviceAddress = "127.0.0.1:7233";
  private String namespace = "default";
  private String taskQueue = "SAGA_TASK_QUEUE";

  // Worker 설정
  private int maxConcurrentActivityExecutionSize = 100;
  private int maxConcurrentWorkflowTaskExecutionSize = 100;
  private int maxConcurrentLocalActivityExecutionSize = 100;

  // Getters and Setters
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

  public int getMaxConcurrentLocalActivityExecutionSize() {
    return maxConcurrentLocalActivityExecutionSize;
  }

  public void setMaxConcurrentLocalActivityExecutionSize(
      int maxConcurrentLocalActivityExecutionSize) {
    this.maxConcurrentLocalActivityExecutionSize = maxConcurrentLocalActivityExecutionSize;
  }
}