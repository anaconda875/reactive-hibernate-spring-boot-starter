package com.htech.jpa.reactive.repository.config;

import org.springframework.data.repository.config.RepositoryBeanDefinitionRegistrarSupport;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;

import java.lang.annotation.Annotation;

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
  private static class EnableReactiveRepositoriesConfiguration {

  }
}
