package com.htech.data.jpa.reactive.repository.config;

import com.htech.data.jpa.reactive.repository.support.ReactiveJpaRepositoryFactoryBean;
import java.lang.annotation.*;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.repository.config.BootstrapMode;
import org.springframework.data.repository.config.DefaultRepositoryBaseClass;
import org.springframework.data.repository.query.QueryLookupStrategy;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(ReactiveJpaRepositoriesRegistrar.class)
public @interface EnableReactiveJpaRepositories {

  String[] value() default {};

  String[] basePackages() default {};

  Class<?>[] basePackageClasses() default {};

  ComponentScan.Filter[] includeFilters() default {};

  ComponentScan.Filter[] excludeFilters() default {};

  String repositoryImplementationPostfix() default "Impl";

  String namedQueriesLocation() default "";

  QueryLookupStrategy.Key queryLookupStrategy() default QueryLookupStrategy.Key.CREATE_IF_NOT_FOUND;

  Class<?> repositoryFactoryBeanClass() default ReactiveJpaRepositoryFactoryBean.class;

  Class<?> repositoryBaseClass() default DefaultRepositoryBaseClass.class;

  // JPA specific configuration

  String sessionFactoryRef() default "sessionFactory";

  String reactiveJpaEntityOperationsRef() default "reactiveJpaEntityTemplate";

  boolean considerNestedRepositories() default false;

  boolean enableDefaultTransactions() default true;

  BootstrapMode bootstrapMode() default BootstrapMode.DEFAULT;

  char escapeCharacter() default '\\';
}
