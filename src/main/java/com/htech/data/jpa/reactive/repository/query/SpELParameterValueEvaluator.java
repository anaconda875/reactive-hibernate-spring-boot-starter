package com.htech.data.jpa.reactive.repository.query;

import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.ReactiveQueryMethodEvaluationContextProvider;
import org.springframework.expression.Expression;
import reactor.core.publisher.Mono;

public class SpELParameterValueEvaluator implements ParameterValueEvaluator {

  private final ReactiveQueryMethodEvaluationContextProvider evaluationContextProvider;
  private final Parameters<?, ?> parameters;
  private final Expression expression;

  public SpELParameterValueEvaluator(
      ReactiveQueryMethodEvaluationContextProvider evaluationContextProvider,
      Parameters<?, ?> parameters,
      Expression expression) {
    this.evaluationContextProvider = evaluationContextProvider;
    this.parameters = parameters;
    this.expression = expression;
  }

  @Override
  public Mono<Object> evaluate(JpaParametersParameterAccessor accessor) {
    return evaluationContextProvider
        .getEvaluationContextLater(parameters, accessor.getValues())
        .map(context -> expression.getValue(context, Object.class));
  }
}
