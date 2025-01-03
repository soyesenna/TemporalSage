package com.senna.TemporalSage.context;

import com.senna.TemporalSage.annotations.GeneratedWorkflow;
import com.senna.TemporalSage.annotations.Workflowable;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import java.util.Arrays;
import java.util.Map;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
public class WorkerFactoryStartListener implements ApplicationListener<ContextRefreshedEvent>,
    ApplicationContextAware {

  private final WorkerFactory workerFactory;
  private ApplicationContext applicationContext;

  public WorkerFactoryStartListener(WorkerFactory workerFactory) {
    this.workerFactory = workerFactory;
  }

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    if (!this.workerFactory.isStarted()) {
      Map<String, Object> beansWithAnnotation = this.applicationContext.getBeansWithAnnotation(
          GeneratedWorkflow.class);

      System.out.println(beansWithAnnotation);

      if (beansWithAnnotation.isEmpty()) {
        return;
      }

      for (Map.Entry<String, Object> entry : beansWithAnnotation.entrySet()) {
        System.out.println(entry.getKey());
        Class<?> targetClass = AopUtils.getTargetClass(entry.getValue());
        Worker worker = this.workerFactory.newWorker(targetClass.getSimpleName());
        worker.registerWorkflowImplementationTypes(targetClass);
      }

      System.out.println("started worker factory");
      this.workerFactory.start();
    }
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }
}