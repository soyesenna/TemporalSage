package com.senna.TemporalSage.saga;

/**
 * SAGA 스텝 파라미터의 메타데이터를 관리하는 클래스
 */
public class ParameterDefinition {

  private final String name;
  private final Class<?> type;

  public ParameterDefinition(String name, Class<?> type) {
    this.name = name;
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public Class<?> getType() {
    return type;
  }
} 