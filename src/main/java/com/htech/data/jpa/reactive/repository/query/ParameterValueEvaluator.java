package com.htech.data.jpa.reactive.repository.query;

import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import reactor.core.publisher.Mono;

public interface ParameterValueEvaluator {

  Mono<Object> evaluate(JpaParametersParameterAccessor accessor);
}
