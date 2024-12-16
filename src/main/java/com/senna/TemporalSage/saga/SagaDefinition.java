package com.senna.TemporalSage.saga;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * SAGA 워크플로우의 메타데이터를 관리하는 클래스
 */
public class SagaDefinition {

  private final String name;
  private final String[] order;
  private final Map<String, StepDefinition> stepsMap;
  private final Map<String, StepDefinition> compensationMap;
  private final SagaContext context;

  public SagaDefinition(String name, String[] order,
      Map<String, StepDefinition> stepsMap,
      Map<String, StepDefinition> compensationMap) {
    this.name = name;
    this.order = order;
    this.stepsMap = stepsMap;
    this.compensationMap = compensationMap;
    this.context = new SagaContext();
  }

  public String getName() {
    return name;
  }

  public String[] getOrder() {
    return order;
  }

  public StepDefinition getStep(String stepName) {
    return stepsMap.get(stepName);
  }

  public StepDefinition getCompensation(String stepName) {
    return compensationMap.get(stepName);
  }

  public void setStepResult(String stepName, Object result) {
    context.setStepResult(stepName, result);
  }

  public void addParameter(String key, Object value) {
    context.addParameter(key, value);
  }

  public Map<String, Object> getParameters() {
    return context.getAllParameters();
  }

  public Map<String, Object> getStepResults() {
    return context.getAllStepResults();
  }

  public Object getStepResult(String stepName) {
    return context.getStepResult(stepName);
  }
} 