package com.senna.TemporalSage.annotations;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CompensationStep {

  /**
   * 보상 트랜잭션이 대응되는 스텝의 이름
   */
  String name();
} 