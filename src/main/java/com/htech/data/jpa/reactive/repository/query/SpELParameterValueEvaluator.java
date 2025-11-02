package com.htech.data.jpa.reactive.repository.query;

import java.util.Optional;
import org.springframework.data.expression.*;
import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import reactor.core.publisher.Mono;

/**
 * @author Bao.Ngo
 */
public class SpELParameterValueEvaluator implements ParameterValueEvaluator {

  private final ValueExpression expression;
  private final ReactiveValueEvaluationContextProvider evaluationContextProvider;

  public SpELParameterValueEvaluator(
      ValueExpression expression,
      ReactiveValueEvaluationContextProvider evaluationContextProvider) {
    this.expression = expression;
    this.evaluationContextProvider = evaluationContextProvider;
  }

  @Override
  public Mono<Optional<Object>> evaluate(JpaParametersParameterAccessor accessor) {
    return evaluationContextProvider
        .getEvaluationContextLater(accessor.getValues())
        .map(context -> Optional.ofNullable(expression.evaluate(context)));
  }
}
