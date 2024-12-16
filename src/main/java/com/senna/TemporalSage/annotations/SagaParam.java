package com.senna.TemporalSage.annotations;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SagaParam {

  /**
   * 파라미터 키 이름
   */
  String value();
} 