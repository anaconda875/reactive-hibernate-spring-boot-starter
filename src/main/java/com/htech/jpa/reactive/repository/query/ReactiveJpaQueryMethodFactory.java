package com.htech.jpa.reactive.repository.query;

import org.springframework.data.jpa.repository.query.JpaQueryMethod;
import org.springframework.data.jpa.repository.query.JpaQueryMethodFactory;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;

import java.lang.reflect.Method;

public class ReactiveJpaQueryMethodFactory implements JpaQueryMethodFactory {

  private final ReactiveJpaQueryExtractor extractor;

  public ReactiveJpaQueryMethodFactory(ReactiveJpaQueryExtractor extractor) {
//    Assert.notNull(extractor, "QueryExtractor must not be null");

    this.extractor = extractor;
  }

  public ReactiveJpaQueryMethod build0(Method method, RepositoryMetadata metadata, ProjectionFactory factory) {
    return new ReactiveJpaQueryMethod(method, metadata, factory, extractor);
  }

  @Override
  public JpaQueryMethod build(Method method, RepositoryMetadata metadata, ProjectionFactory factory) {
    throw new UnsupportedOperationException();
  }
}
