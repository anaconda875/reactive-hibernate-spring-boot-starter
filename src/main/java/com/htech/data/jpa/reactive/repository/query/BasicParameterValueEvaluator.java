package com.htech.data.jpa.reactive.repository.query;

import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import reactor.core.publisher.Mono;

public class BasicParameterValueEvaluator implements ParameterValueEvaluator {

  private final ReactiveJpaParameters.JpaParameter parameter;

  public BasicParameterValueEvaluator(ReactiveJpaParameters.JpaParameter parameter) {
    this.parameter = parameter;
  }

  @Override
  public Mono<Object> evaluate(JpaParametersParameterAccessor accessor) {
    return Mono.just(accessor).map(a -> a.getValue(parameter));
  }
}
