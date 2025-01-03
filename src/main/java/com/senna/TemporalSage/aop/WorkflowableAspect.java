package com.senna.TemporalSage.aop;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class WorkflowableAspect {

  private final ApplicationContext applicationContext;

  @Around("@annotation(com.senna.TemporalSage.annotations.Workflowable)")
  public Object workflowable(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
    MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
    Object bean = this.applicationContext.getBean(this.getBeanName(signature.getName()));
    return bean.getClass().getDeclaredMethod(signature.getName(), signature.getParameterTypes()).invoke(bean, proceedingJoinPoint.getArgs());
  }

  private String getBeanName(String methodName) {
    StringBuilder sb = new StringBuilder();
    return sb.append(methodName).append("WorkflowInterfaceImpl").toString();
  }
}
