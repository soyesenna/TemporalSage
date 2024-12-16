package com.senna.TemporalSage.config;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import java.time.Duration;

/**
 * SAGA Activity의 설정을 관리하는 클래스
 */
public class SagaActivityOptions {

  public static ActivityOptions.Builder defaultOptions() {
    return ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofMinutes(5))
        .setScheduleToCloseTimeout(Duration.ofMinutes(10))
        .setRetryOptions(defaultRetryOptions());
  }

  private static RetryOptions defaultRetryOptions() {
    return RetryOptions.newBuilder()
        .setInitialInterval(Duration.ofSeconds(1))
        .setMaximumInterval(Duration.ofMinutes(1))
        .setBackoffCoefficient(2.0)
        .setMaximumAttempts(3)
        .build();
  }
} 