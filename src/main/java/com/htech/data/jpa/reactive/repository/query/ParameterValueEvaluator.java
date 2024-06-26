package com.htech.data.jpa.reactive.repository.query;

import java.util.Optional;
import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import reactor.core.publisher.Mono;

public interface ParameterValueEvaluator {

  Mono<Optional<Object>> evaluate(JpaParametersParameterAccessor accessor);
}
