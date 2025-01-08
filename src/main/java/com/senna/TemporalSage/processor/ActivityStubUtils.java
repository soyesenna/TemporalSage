package com.senna.TemporalSage.processor;

import com.senna.TemporalSage.saga.SagaActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActivityStubUtils {

  private final ApplicationContext applicationContext;

  public SagaActivity createActivityStub(Class<? extends SagaActivity> activityInterfaceImpl, ActivityOptions activityOptions) {
    StringBuilder sb = new StringBuilder();
    sb.append(this.getMethodName(activityInterfaceImpl)).append("Options");

    return Workflow.newActivityStub(activityInterfaceImpl, activityOptions);
  }

  private String getMethodName(Class<? extends SagaActivity> activityInterfaceImpl) {
    String methodName = activityInterfaceImpl.getSimpleName();
    return methodName.substring(0, 1).toLowerCase() + methodName.substring(1);
  }
}
