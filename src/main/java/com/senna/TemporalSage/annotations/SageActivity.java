package com.senna.TemporalSage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Component;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface SageActivity {

  // Timeouts
  long startToCloseTimeoutSeconds() default 10;
  long scheduleToCloseTimeoutSeconds() default 30;
  long scheduleToStartTimeoutSeconds() default 10;
  long heartbeatTimeoutSeconds() default 10;

  // Retry
  int retryMaxAttempts() default 10;
  double retryBackoffCoefficient() default 2.0;
  long retryInitialIntervalSeconds() default 1;
  long retryMaximumIntervalSeconds() default 100;
}
