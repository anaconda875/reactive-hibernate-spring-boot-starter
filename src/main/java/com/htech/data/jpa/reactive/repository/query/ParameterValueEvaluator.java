package com.htech.data.jpa.reactive.repository.query;

import java.util.Optional;

import org.springframework.data.expression.ValueExpression;
import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import reactor.core.publisher.Mono;

/**
 * @author Bao.Ngo
 */
public interface ParameterValueEvaluator {

  Mono<Optional<Object>> evaluate(JpaParametersParameterAccessor accessor);
}
