package com.senna.TemporalSage.saga;

import io.temporal.workflow.Workflow;
import java.util.ArrayList;
import java.util.List;

/**
 * SAGA 워크플로우의 기본 구현을 제공하는 추상 클래스
 */
public abstract class AbstractSagaWorkflow {

  private final List<SagaStep<?, ?>> steps = new ArrayList<>();
  private int currentStepIndex = -1;
  private final SagaContext context;

  protected AbstractSagaWorkflow() {
    this.context = new SagaContext();
  }

  protected void addStep(SagaStep<?, ?> step) {
    steps.add(step);
  }

  protected void execute() {
    try {
      for (int i = 0; i < steps.size(); i++) {
        currentStepIndex = i;
        SagaStep<?, ?> step = steps.get(i);
        Object result = step.execute(context);
        context.setStepResult("step-" + i, result);
      }
    } catch (Exception e) {
      compensate();
      throw e;
    }
  }

  private void compensate() {
    for (int i = currentStepIndex; i >= 0; i--) {
      try {
        steps.get(i).compensate(context);
      } catch (Exception ce) {
        Workflow.getLogger(getClass()).error("Compensation failed for step " + i, ce);
      }
    }
  }

  protected void addParameter(String key, Object value) {
    context.addParameter(key, value);
  }

  protected Object getStepResult(String stepName) {
    return context.getStepResult(stepName);
  }
} 