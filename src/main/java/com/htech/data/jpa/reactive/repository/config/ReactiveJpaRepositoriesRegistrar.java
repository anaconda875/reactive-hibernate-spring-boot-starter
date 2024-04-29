package com.htech.data.jpa.reactive.repository.config;

import java.lang.annotation.Annotation;
import org.springframework.data.repository.config.RepositoryBeanDefinitionRegistrarSupport;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;

class ReactiveJpaRepositoriesRegistrar extends RepositoryBeanDefinitionRegistrarSupport {

  @Override
  protected Class<? extends Annotation> getAnnotation() {
    return EnableReactiveJpaRepositories.class;
  }

  @Override
  protected RepositoryConfigurationExtension getExtension() {
    return new ReactiveJpaRepositoryConfigExtension();
  }

  //  @Override
  //  protected Class<?> getConfiguration() {
  //    return EnableReactiveRepositoriesConfiguration.class;
  //  }

  //  @Override
  //  protected RepositoryConfigurationExtension getRepositoryConfigurationExtension() {
  //    return new ReactiveJpaRepositoryConfigExtension();
  //  }

  @EnableReactiveJpaRepositories
  private static class EnableReactiveRepositoriesConfiguration {}
}
