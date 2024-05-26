package com.htech.data.jpa.reactive.config;

import java.lang.annotation.*;
import org.springframework.context.annotation.Import;

@Inherited
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ReactiveJpaAuditingRegistrar.class)
public @interface EnableReactiveJpaAuditing {

  String auditorAwareRef() default "";

  boolean setDates() default true;

  boolean modifyOnCreate() default true;

  String dateTimeProviderRef() default "";
}
