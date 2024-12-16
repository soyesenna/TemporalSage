package com.senna.TemporalSage.execution;

import com.senna.TemporalSage.saga.*;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.Map;

/**
 * SAGA 워크플로우의 실행을 조정하는 코디네이터
 */
public class SagaExecutionCoordinator {

  private final SagaDefinition sagaDefinition;
  private final Object workflowInstance;
  private final ActivityOptions activityOptions;

  public SagaExecutionCoordinator(SagaDefinition sagaDefinition,
      Object workflowInstance,
      ActivityOptions activityOptions) {
    this.sagaDefinition = sagaDefinition;
    this.workflowInstance = workflowInstance;
    this.activityOptions = activityOptions;
  }

  public void executeSaga(Map<String, Object> context) {
    String[] order = sagaDefinition.getOrder();
    int currentStep = -1;

    try {
      for (currentStep = 0; currentStep < order.length; currentStep++) {
        String stepName = order[currentStep];
        StepDefinition stepDef = sagaDefinition.getStep(stepName);
        executeStep(stepDef, context);
      }
    } catch (Exception e) {
      compensateFrom(currentStep, context);
      throw e;
    }
  }

  private void executeStep(StepDefinition stepDef, Map<String, Object> context) {
    try {
      // Temporal Activity로 실행
      Object[] args = resolveParameters(stepDef, context);
      Workflow.newActivityStub(Object.class, activityOptions)
          .execute(stepDef.getMethod().getName(), args);
    } catch (Exception e) {
      throw new RuntimeException("Failed to execute step: " + stepDef.getName(), e);
    }
  }

  private void compensateFrom(int fromStep, Map<String, Object> context) {
    String[] order = sagaDefinition.getOrder();
    for (int i = fromStep; i >= 0; i--) {
      String stepName = order[i];
      StepDefinition compDef = sagaDefinition.getCompensation(stepName);
      try {
        Object[] args = resolveParameters(compDef, context);
        Workflow.newActivityStub(Object.class, activityOptions)
            .execute(compDef.getMethod().getName(), args);
      } catch (Exception e) {
        Workflow.getLogger(getClass())
            .error("Compensation failed for step: " + stepName, e);
      }
    }
  }

  private Object[] resolveParameters(StepDefinition stepDef, Map<String, Object> context) {
    ParameterDefinition[] params = stepDef.getParameters();
    Object[] args = new Object[params.length];

    for (int i = 0; i < params.length; i++) {
      ParameterDefinition param = params[i];
      args[i] = context.get(param.getName());
    }

    return args;
  }
} 