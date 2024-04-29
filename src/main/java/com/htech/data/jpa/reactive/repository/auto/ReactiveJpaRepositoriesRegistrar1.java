package com.htech.data.jpa.reactive.repository.auto;

import com.htech.data.jpa.reactive.repository.config.EnableReactiveJpaRepositories;
import com.htech.data.jpa.reactive.repository.config.ReactiveJpaRepositoryConfigExtension;
import java.lang.annotation.Annotation;
import org.springframework.boot.autoconfigure.data.AbstractRepositoryConfigurationSourceSupport;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;

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
  private static class EnableReactiveRepositoriesConfiguration {}
}
