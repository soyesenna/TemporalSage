package com.senna.TemporalSage.annotations;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SagaWorkflow {

  /**
   * SAGA 워크플로우의 이름
   */
  String name() default "";

  /**
   * 실행할 스텝들의 순서
   */
  String[] order();
} 