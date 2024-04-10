package com.htech.jpa.reactive.repository.auto;

import com.htech.jpa.reactive.repository.config.EnableReactiveJpaRepositories;
import com.htech.jpa.reactive.repository.config.ReactiveJpaRepositoryConfigExtension;
import org.springframework.boot.autoconfigure.data.AbstractRepositoryConfigurationSourceSupport;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;

import java.lang.annotation.Annotation;

class ReactiveJpaRepositoriesRegistrar1 extends AbstractRepositoryConfigurationSourceSupport {

  @Override
  protected Class<? extends Annotation> getAnnotation() {
    return EnableReactiveJpaRepositories.class;
  }

  @Override
  protected Class<?> getConfiguration() {
    return EnableReactiveRepositoriesConfiguration.class;
  }

  @Override
  protected RepositoryConfigurationExtension getRepositoryConfigurationExtension() {
    return new ReactiveJpaRepositoryConfigExtension();
  }

  @EnableReactiveJpaRepositories
  private static class EnableReactiveRepositoriesConfiguration {

  }
}
