package com.senna.TemporalSage.configs;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.common.converter.JacksonJsonPayloadConverter;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class TemporalSageAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public WorkflowServiceStubs workflowServiceStubs(TemporalSageProperties properties) {
    WorkflowServiceStubsOptions options = WorkflowServiceStubsOptions.newBuilder()
        .setTarget(properties.getServiceAddress())
        .build();
    return WorkflowServiceStubs.newServiceStubs(options);
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs,
      TemporalSageProperties properties,
      DataConverter dataConverter) {
    return WorkflowClient.newInstance(serviceStubs,
        WorkflowClientOptions.newBuilder()
            .setNamespace(properties.getNamespace())
            .setDataConverter(dataConverter)
            .build());
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkerOptions workerOptions(TemporalSageProperties properties) {
    return WorkerOptions.newBuilder()
        .setMaxConcurrentActivityExecutionSize(properties.getMaxConcurrentActivityExecutionSize())
        .setMaxConcurrentWorkflowTaskExecutionSize(
            properties.getMaxConcurrentWorkflowTaskExecutionSize())
        .build();
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkerFactory workerFactory(WorkflowClient workflowClient) {
    return WorkerFactory.newInstance(workflowClient);
  }

  @Bean
  @ConditionalOnMissingBean
  public DataConverter dataConverter() {
    // Jackson ObjectMapper 커스터마이징
    ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        // 모든 접근 제어자의 필드에 접근 허용
        .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
        // 타입 정보 포함
        .activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        )
        // null 값 포함
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        // 순환 참조 처리
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);

    // 커스텀 모듈 등록
    SimpleModule module = new SimpleModule();
    module.addDeserializer(Class.class, new ClassDeserializer());
    objectMapper.registerModule(module);

    // 커스텀 JacksonJsonPayloadConverter 생성
    JacksonJsonPayloadConverter jacksonConverter = new JacksonJsonPayloadConverter(objectMapper);

    // DefaultDataConverter와 함께 사용
    return DefaultDataConverter.newDefaultInstance()
        .withPayloadConverterOverrides(jacksonConverter);
  }

  // Class 타입을 위한 커스텀 Deserializer
  private static class ClassDeserializer extends JsonDeserializer<Class<?>> {

    @Override
    public Class<?> deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
      String className = p.getValueAsString();
      try {
        return Class.forName(className);
      } catch (ClassNotFoundException e) {
        throw new IOException("Failed to deserialize class: " + className, e);
      }
    }
  }
}