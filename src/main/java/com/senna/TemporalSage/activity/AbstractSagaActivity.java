package com.senna.TemporalSage.activity;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;

/**
 * SAGA Activity의 기본 구현을 제공하는 추상 클래스
 */
public abstract class AbstractSagaActivity<T, R> implements SagaActivity<T, R> {
    
    protected final ActivityExecutionContext getExecutionContext() {
        return Activity.getExecutionContext();
    }
    
    protected void heartbeat(Object... details) {
        getExecutionContext().heartbeat(details);
    }
    
    @Override
    public abstract R execute(T context);
    
    @Override
    public abstract void compensate(T context);
} 