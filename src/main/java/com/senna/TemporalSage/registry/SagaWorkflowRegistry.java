package com.senna.TemporalSage.registry;

import com.senna.TemporalSage.annotations.*;
import com.senna.TemporalSage.annotations.SagaStep;
import com.senna.TemporalSage.saga.*;
import io.temporal.workflow.Workflow;
import java.lang.reflect.Method;
import java.util.*;

/**
 * SAGA 워크플로우 정의를 관리하는 레지스트리
 */
public class SagaWorkflowRegistry {

  private final Map<Class<?>, SagaDefinition> sagaDefinitions = new HashMap<>();

  public void registerSagaWorkflow(Class<?> workflowClass) {
    if (!workflowClass.isAnnotationPresent(SagaWorkflow.class)) {
      throw new IllegalArgumentException("Class must be annotated with @SagaWorkflow");
    }

    SagaWorkflow sagaAnno = workflowClass.getAnnotation(SagaWorkflow.class);
    String name = sagaAnno.name().isEmpty() ? workflowClass.getSimpleName() : sagaAnno.name();
    String[] order = sagaAnno.order();

    Map<String, StepDefinition> stepsMap = new HashMap<>();
    Map<String, StepDefinition> compensationMap = new HashMap<>();

    // 메서드 스캔하여 Step과 Compensation 매핑
    for (Method method : workflowClass.getDeclaredMethods()) {
      if (method.isAnnotationPresent(SagaStep.class)) {
        SagaStep stepAnno = method.getAnnotation(SagaStep.class);
        stepsMap.put(stepAnno.name(), new StepDefinition(stepAnno.name(), method));
      } else if (method.isAnnotationPresent(CompensationStep.class)) {
        CompensationStep compAnno = method.getAnnotation(CompensationStep.class);
        compensationMap.put(compAnno.name(), new StepDefinition(compAnno.name(), method));
      }
    }

    // 검증: 모든 step에 대응하는 compensation이 있는지 확인
    validateStepCompensationPairs(order, stepsMap, compensationMap);

    sagaDefinitions.put(workflowClass, new SagaDefinition(name, order, stepsMap, compensationMap));
  }

  private void validateStepCompensationPairs(String[] order,
      Map<String, StepDefinition> stepsMap,
      Map<String, StepDefinition> compensationMap) {
    for (String stepName : order) {
      if (!stepsMap.containsKey(stepName)) {
        throw new IllegalStateException("Missing step definition for: " + stepName);
      }
      if (!compensationMap.containsKey(stepName)) {
        throw new IllegalStateException("Missing compensation for step: " + stepName);
      }
    }
  }

  public SagaDefinition getSagaDefinition(Class<?> workflowClass) {
    SagaDefinition definition = sagaDefinitions.get(workflowClass);
    if (definition == null) {
      throw new IllegalStateException("No saga definition found for: " + workflowClass.getName());
    }
    return definition;
  }
} 