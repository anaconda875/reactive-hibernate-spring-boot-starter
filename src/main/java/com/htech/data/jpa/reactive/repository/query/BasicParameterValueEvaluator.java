package com.htech.data.jpa.reactive.repository.query;

import java.util.Optional;
import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import reactor.core.publisher.Mono;

public class BasicParameterValueEvaluator implements ParameterValueEvaluator {

  private final ReactiveJpaParameters.JpaParameter parameter;

  public BasicParameterValueEvaluator(ReactiveJpaParameters.JpaParameter parameter) {
    this.parameter = parameter;
  }

  @Override
  public Mono<Optional<Object>> evaluate(JpaParametersParameterAccessor accessor) {
    return Mono.just(accessor).map(a -> Optional.ofNullable(a.getValue(parameter)));
  }
}
