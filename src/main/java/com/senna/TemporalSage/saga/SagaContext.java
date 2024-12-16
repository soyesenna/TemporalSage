package com.senna.TemporalSage.saga;

import java.util.HashMap;
import java.util.Map;

/**
 * SAGA 워크플로우의 실행 컨텍스트를 관리하는 클래스
 */
public class SagaContext {
    private final Map<String, Object> parameters;
    private final Map<String, Object> stepResults;

    public SagaContext() {
        this.parameters = new HashMap<>();
        this.stepResults = new HashMap<>();
    }

    public void addParameter(String key, Object value) {
        parameters.put(key, value);
    }

    public Object getParameter(String key) {
        return parameters.get(key);
    }

    public void setStepResult(String stepName, Object result) {
        stepResults.put(stepName, result);
    }

    public Object getStepResult(String stepName) {
        return stepResults.get(stepName);
    }

    public Map<String, Object> getAllParameters() {
        return new HashMap<>(parameters);
    }

    public Map<String, Object> getAllStepResults() {
        return new HashMap<>(stepResults);
    }
} 