package com.htech.data.jpa.reactive.repository.query;

import java.lang.reflect.Method;
import org.springframework.data.jpa.repository.query.JpaQueryMethod;
import org.springframework.data.jpa.repository.query.JpaQueryMethodFactory;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;

/**
 * @author Bao.Ngo
 */
public class ReactiveJpaQueryMethodFactory implements JpaQueryMethodFactory {

  private final ReactiveJpaQueryExtractor extractor;

  public ReactiveJpaQueryMethodFactory(ReactiveJpaQueryExtractor extractor) {
    this.extractor = extractor;
  }

  public ReactiveJpaQueryMethod build0(
      Method method, RepositoryMetadata metadata, ProjectionFactory factory) {
    return new ReactiveJpaQueryMethod(method, metadata, factory, extractor);
  }

  @Override
  public JpaQueryMethod build(
      Method method, RepositoryMetadata metadata, ProjectionFactory factory) {
    throw new UnsupportedOperationException();
  }
}
