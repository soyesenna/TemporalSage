package com.senna.TemporalSage.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * SAGA 패턴의 Activity를 정의하는 기본 인터페이스
 */
@ActivityInterface
public interface SagaActivity<T, R> {

  @ActivityMethod
  R execute(T context);

  @ActivityMethod
  void compensate(T context);
} 