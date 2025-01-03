package com.senna.TemporalSage.annotations;

import com.senna.TemporalSage.configs.TemporalSageImportConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import({
    TemporalSageImportConfiguration.class
})
@ComponentScan(basePackages = "")
public @interface EnableTemporalSage {

}
