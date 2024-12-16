package com.senna.TemporalSage.config;

import com.senna.TemporalSage.registry.SagaWorkflowRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TemporalSageProperties.class)
public class TemporalSageAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public WorkflowServiceStubs workflowServiceStubs(TemporalSageProperties properties) {
    return WorkflowServiceStubs.newInstance(
        WorkflowServiceStubsOptions.newBuilder()
            .setTarget(properties.getServiceAddress())
            .build()
    );
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs,
      TemporalSageProperties properties) {
    return WorkflowClient.newInstance(
        serviceStubs,
        WorkflowClientOptions.newBuilder()
            .setNamespace(properties.getNamespace())
            .build()
    );
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkerFactory workerFactory(WorkflowClient workflowClient) {
    return WorkerFactory.newInstance(workflowClient);
  }

  @Bean
  @ConditionalOnMissingBean
  public SagaWorkflowRegistry sagaWorkflowRegistry() {
    return new SagaWorkflowRegistry();
  }
} 