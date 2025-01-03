package com.senna.TemporalSage.configs;

import com.senna.TemporalSage.processor.ActivityProcessor;
import com.senna.TemporalSage.processor.ActivityStubUtils;
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
    ActivityProcessor.class,

    // utils
    ActivityStubUtils.class
})
public class TemporalSageImportConfiguration {
}