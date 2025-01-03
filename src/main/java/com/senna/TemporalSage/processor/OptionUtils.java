package com.senna.TemporalSage.processor;

import com.senna.TemporalSage.annotations.Option;
import com.squareup.javapoet.CodeBlock;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.lang.reflect.Method;
import java.time.Duration;

public class OptionUtils {

  // 리플렉션으로 Option 어노테이션의 'default' 값을 얻어오는 헬퍼 메서드
  private static Object getAnnotationDefaultValue(String methodName) {
    try {
      Method method = Option.class.getMethod(methodName);
      return method.getDefaultValue();
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  // options가 null이면 기본값(리플렉션으로 추출), 아니면 options에 설정된 값 사용
  private static int getRetryMaxAttempts(Option options) {
    return (options != null)
        ? options.retryMaxAttempts()
        : (int) getAnnotationDefaultValue("retryMaxAttempts");
  }

  private static double getRetryBackoffCoefficient(Option options) {
    return (options != null)
        ? options.retryBackoffCoefficient()
        : (double) getAnnotationDefaultValue("retryBackoffCoefficient");
  }

  private static long getRetryInitialIntervalSeconds(Option options) {
    return (options != null)
        ? options.retryInitialIntervalSeconds()
        : (long) getAnnotationDefaultValue("retryInitialIntervalSeconds");
  }

  private static long getRetryMaximumIntervalSeconds(Option options) {
    return (options != null)
        ? options.retryMaximumIntervalSeconds()
        : (long) getAnnotationDefaultValue("retryMaximumIntervalSeconds");
  }

  private static long getHeartbeatTimeoutSeconds(Option options) {
    return (options != null)
        ? options.heartbeatTimeoutSeconds()
        : (long) getAnnotationDefaultValue("heartbeatTimeoutSeconds");
  }

  private static long getStartToCloseTimeoutSeconds(Option options) {
    return (options != null)
        ? options.startToCloseTimeoutSeconds()
        : (long) getAnnotationDefaultValue("startToCloseTimeoutSeconds");
  }

  private static long getScheduleToCloseTimeoutSeconds(Option options) {
    return (options != null)
        ? options.scheduleToCloseTimeoutSeconds()
        : (long) getAnnotationDefaultValue("scheduleToCloseTimeoutSeconds");
  }

  private static long getScheduleToStartTimeoutSeconds(Option options) {
    return (options != null)
        ? options.scheduleToStartTimeoutSeconds()
        : (long) getAnnotationDefaultValue("scheduleToStartTimeoutSeconds");
  }

  private static boolean getWaitForCancellation(Option options) {
    return (options != null)
        ? options.waitForCancellation()
        : (boolean) getAnnotationDefaultValue("waitForCancellation");
  }

  /**
   * options가 null이면 Option 어노테이션의 default 값을 사용해서,
   * null이 아니면 어노테이션에 설정된 값을 이용해서
   * ActivityOptions 생성 코드를 Javapoet CodeBlock 형태로 반환.
   */
  public static CodeBlock createActivityOptions(Option options, String taskQueue) {

    // 1) 우선, 실제로 사용할 파라미터(어노테이션 값 or 기본값)를 미리 변수로 꺼내둠
    int retryMaxAttempts = getRetryMaxAttempts(options);
    double backoffCoefficient = getRetryBackoffCoefficient(options);
    long retryInitialInterval = getRetryInitialIntervalSeconds(options);
    long retryMaximumInterval = getRetryMaximumIntervalSeconds(options);
    long heartbeatTimeout = getHeartbeatTimeoutSeconds(options);
    long startToCloseTimeout = getStartToCloseTimeoutSeconds(options);
    long scheduleToCloseTimeout = getScheduleToCloseTimeoutSeconds(options);
    long scheduleToStartTimeout = getScheduleToStartTimeoutSeconds(options);
    boolean waitForCancellation = getWaitForCancellation(options);

    // 2) CodeBlock 빌더로 ActivityOptions 생성 코드를 작성
    return CodeBlock.builder()
        .add("$T activityOptions = $T.newBuilder()\n",
            ActivityOptions.class, ActivityOptions.class)
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