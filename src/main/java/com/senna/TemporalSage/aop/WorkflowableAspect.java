package com.senna.TemporalSage.aop;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.workflow.WorkflowInterface;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class WorkflowableAspect {

  private final ApplicationContext applicationContext;
  private final WorkflowClient workflowClient;

  @Around("@annotation(com.senna.TemporalSage.annotations.Workflowable)")
  public Object workflowable(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
    MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
    Object bean = this.applicationContext.getBean(this.getBeanName(signature.getName()));
    Class<?> noProxyBean = AopUtils.getTargetClass(bean);

    Class<?>[] beanInterfaces = noProxyBean.getInterfaces();

    Class<?> workflowInterface = null;

    for (Class<?> beanInterface : beanInterfaces) {
      if (beanInterface.isAnnotationPresent(WorkflowInterface.class)) {
        workflowInterface = beanInterface;
        break;
      }
    }

    if (workflowInterface == null) {
      throw new RuntimeException("Workflow interface not found");
    }

    WorkflowOptions workflowOptions = WorkflowOptions.newBuilder()
        .setTaskQueue(noProxyBean.getSimpleName())
        .build();

    Object stub = this.workflowClient.newWorkflowStub(workflowInterface, workflowOptions);

    return stub.getClass().getDeclaredMethod(signature.getName(), signature.getParameterTypes()).invoke(stub, proceedingJoinPoint.getArgs());
  }

  private String getBeanName(String methodName) {
    StringBuilder sb = new StringBuilder();
    return sb.append(methodName).append("WorkflowInterfaceImpl").toString();
  }
}
