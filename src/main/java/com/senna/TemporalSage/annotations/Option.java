package com.senna.TemporalSage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Option {

  // Timeouts (초 단위, 혹은 java.time.Duration을 직접 쓸 수도 있음)
  long startToCloseTimeoutSeconds() default 10;
  long scheduleToCloseTimeoutSeconds() default 30;
  long scheduleToStartTimeoutSeconds() default 0;
  long heartbeatTimeoutSeconds() default 0;

  // Retry 관련
  int retryMaxAttempts() default 0;
  double retryBackoffCoefficient() default 2.0;
  long retryInitialIntervalSeconds() default 1;
  long retryMaximumIntervalSeconds() default 100;

  // Wait for cancellation
  boolean waitForCancellation() default false;
}
