package com.senna.TemporalSage.saga;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface SagaActivity<T, R> {

  @ActivityMethod
  R execute(T input);

  @ActivityMethod
  void compensate(T input);
}
