package com.senna.TemporalSage.saga;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * SAGA 스텝의 메타데이터를 관리하는 클래스
 */
public class StepDefinition {

  private final String name;
  private final Method method;
  private final ParameterDefinition[] parameters;

  public StepDefinition(String name, Method method) {
    this.name = name;
    this.method = method;
    this.parameters = parseParameters(method);
  }

  private ParameterDefinition[] parseParameters(Method method) {
    Parameter[] params = method.getParameters();
    ParameterDefinition[] pdefs = new ParameterDefinition[params.length];

    for (int i = 0; i < params.length; i++) {
      Parameter p = params[i];
      pdefs[i] = new ParameterDefinition(p.getName(), p.getType());
    }

    return pdefs;
  }

  public String getName() {
    return name;
  }

  public Method getMethod() {
    return method;
  }

  public ParameterDefinition[] getParameters() {
    return parameters;
  }
} 