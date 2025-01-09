package com.senna.TemporalSage.configs;

import com.senna.TemporalSage.aop.WorkflowableAspect;
import com.senna.TemporalSage.context.WorkerFactoryStartListener;
import com.senna.TemporalSage.processor.SageProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    // configurations
    TemporalSageAutoConfiguration.class,
    TemporalSageProperties.class,

    // processor
    SageProcessor.class,

    // aspect
    WorkflowableAspect.class,

    // listener
    WorkerFactoryStartListener.class
})
public class TemporalSageImportConfiguration {
}