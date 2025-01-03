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

  public SagaActivity createActivityStub(Class<? extends SagaActivity> activityInterfaceImpl) {
    StringBuilder sb = new StringBuilder();
    sb.append(this.getMethodName(activityInterfaceImpl)).append("Options");

    ActivityOptions activityOption = this.applicationContext.getBean(
        sb.toString(), ActivityOptions.class);

    return Workflow.newActivityStub(activityInterfaceImpl, activityOption);
  }

  private String getMethodName(Class<? extends SagaActivity> activityInterfaceImpl) {
    String methodName = activityInterfaceImpl.getSimpleName();
    return methodName.substring(0, 1).toLowerCase() + methodName.substring(1);
  }
}
