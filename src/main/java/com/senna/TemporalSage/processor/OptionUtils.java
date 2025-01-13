package com.senna.TemporalSage.processor;

import com.senna.TemporalSage.annotations.SageActivity;
import com.squareup.javapoet.CodeBlock;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import java.lang.reflect.Method;
import java.time.Duration;

public class OptionUtils {

  private static Object getAnnotationDefaultValue(String methodName) {
    try {
      Method method = SageActivity.class.getMethod(methodName);
      return method.getDefaultValue();
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private static int getRetryMaxAttempts(SageActivity options) {
    return (options != null)
        ? options.retryMaxAttempts()
        : (int) getAnnotationDefaultValue("retryMaxAttempts");
  }

  private static double getRetryBackoffCoefficient(SageActivity options) {
    return (options != null)
        ? options.retryBackoffCoefficient()
        : (double) getAnnotationDefaultValue("retryBackoffCoefficient");
  }

  private static long getRetryInitialIntervalSeconds(SageActivity options) {
    return (options != null)
        ? options.retryInitialIntervalSeconds()
        : (long) getAnnotationDefaultValue("retryInitialIntervalSeconds");
  }

  private static long getRetryMaximumIntervalSeconds(SageActivity options) {
    return (options != null)
        ? options.retryMaximumIntervalSeconds()
        : (long) getAnnotationDefaultValue("retryMaximumIntervalSeconds");
  }

  private static long getHeartbeatTimeoutSeconds(SageActivity options) {
    return (options != null)
        ? options.heartbeatTimeoutSeconds()
        : (long) getAnnotationDefaultValue("heartbeatTimeoutSeconds");
  }

  private static long getStartToCloseTimeoutSeconds(SageActivity options) {
    return (options != null)
        ? options.startToCloseTimeoutSeconds()
        : (long) getAnnotationDefaultValue("startToCloseTimeoutSeconds");
  }

  private static long getScheduleToCloseTimeoutSeconds(SageActivity options) {
    return (options != null)
        ? options.scheduleToCloseTimeoutSeconds()
        : (long) getAnnotationDefaultValue("scheduleToCloseTimeoutSeconds");
  }

  private static long getScheduleToStartTimeoutSeconds(SageActivity options) {
    return (options != null)
        ? options.scheduleToStartTimeoutSeconds()
        : (long) getAnnotationDefaultValue("scheduleToStartTimeoutSeconds");
  }

  public static CodeBlock createActivityOptions(SageActivity options, String taskQueue) {
    int retryMaxAttempts = getRetryMaxAttempts(options);
    double backoffCoefficient = getRetryBackoffCoefficient(options);
    long retryInitialInterval = getRetryInitialIntervalSeconds(options);
    long retryMaximumInterval = getRetryMaximumIntervalSeconds(options);
    long heartbeatTimeout = getHeartbeatTimeoutSeconds(options);
    long startToCloseTimeout = getStartToCloseTimeoutSeconds(options);
    long scheduleToCloseTimeout = getScheduleToCloseTimeoutSeconds(options);
    long scheduleToStartTimeout = getScheduleToStartTimeoutSeconds(options);

    return CodeBlock.builder()
        .add("$T $N = $T.newBuilder()\n",
            ActivityOptions.class, taskQueue, ActivityOptions.class)
        .indent()
        // RetryOptions 설정
        .add(".setRetryOptions($T.newBuilder()\n", RetryOptions.class)
        .indent()
        .add(".setMaximumAttempts($L)\n", retryMaxAttempts)          // int
        .add(".setBackoffCoefficient($L)\n", backoffCoefficient)     // double
        .add(".setInitialInterval($T.ofSeconds($L))\n",
            Duration.class, retryInitialInterval)
        .add(".setMaximumInterval($T.ofSeconds($L))\n",
            Duration.class, retryMaximumInterval)
        .add(".build())\n")
        .unindent()
        // 기타 옵션 설정
        .add(".setTaskQueue($S)\n", taskQueue)
        .add(".setHeartbeatTimeout($T.ofSeconds($L))\n",
            Duration.class, heartbeatTimeout)
        .add(".setStartToCloseTimeout($T.ofSeconds($L))\n",
            Duration.class, startToCloseTimeout)
        .add(".setScheduleToCloseTimeout($T.ofSeconds($L))\n",
            Duration.class, scheduleToCloseTimeout)
        .add(".setScheduleToStartTimeout($T.ofSeconds($L))\n",
            Duration.class, scheduleToStartTimeout)
        .add(".build();\n")  // activityOptions 할당 종료
        .unindent()
        .build();
  }
}