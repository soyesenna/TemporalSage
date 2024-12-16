package com.senna.TemporalSage.annotations;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SagaStep {

  /**
   * 스텝의 이름
   */
  String name();
} 