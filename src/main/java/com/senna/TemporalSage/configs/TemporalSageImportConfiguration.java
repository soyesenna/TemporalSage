package com.senna.TemporalSage.configs;

import com.senna.TemporalSage.processor.SageProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    // configurations
    TemporalSageAutoConfiguration.class,
    TemporalSageProperties.class,

    // processor
    SageProcessor.class
})
public class TemporalSageImportConfiguration {
}