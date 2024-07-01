package com.htech.data.jpa.reactive.repository.query;

import org.hibernate.reactive.stage.Stage;
import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import org.springframework.data.jpa.support.PageableUtils;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Bao.Ngo
 */
public class ParameterBinder {

  static final String PARAMETER_NEEDS_TO_BE_NAMED =
      "For queries with named parameters you need to provide names for method parameters; Use @Param for query method parameters, or when on Java 8+ use the javac flag -parameters";

  private final ReactiveJpaParameters parameters;
  private final Iterable<QueryParameterSetter> parameterSetters;
  private final boolean useJpaForPaging;

  ParameterBinder(
      ReactiveJpaParameters parameters, Iterable<QueryParameterSetter> parameterSetters) {
    this(parameters, parameterSetters, true);
  }

  public ParameterBinder(
      ReactiveJpaParameters parameters,
      Iterable<QueryParameterSetter> parameterSetters,
      boolean useJpaForPaging) {

    Assert.notNull(parameters, "ReactiveJpaParameters must not be null");
    Assert.notNull(parameterSetters, "Parameter setters must not be null");

    this.parameters = parameters;
    this.parameterSetters = parameterSetters;
    this.useJpaForPaging = useJpaForPaging;
  }

  public <T extends Stage.AbstractQuery> Mono<T> bind(
      T jpaQuery,
      QueryParameterSetter.QueryMetadata metadata,
      JpaParametersParameterAccessor accessor) {

    return bind(metadata.withQuery(jpaQuery), accessor, QueryParameterSetter.ErrorHandling.STRICT)
        .thenReturn(jpaQuery);
    //    return jpaQuery;
  }

  public Mono<Void> bind(
      QueryParameterSetter.BindableQuery query,
      JpaParametersParameterAccessor accessor,
      QueryParameterSetter.ErrorHandling errorHandling) {

    //    for (QueryParameterSetter setter : parameterSetters) {
    //      setter.setParameter(query, accessor, errorHandling);
    //    }
    return Flux.fromIterable(parameterSetters)
        .concatMap(setter -> setter.setParameter(query, accessor, errorHandling))
        .then();
  }

  Mono<Stage.AbstractQuery> bindAndPrepare(
      Stage.AbstractQuery query,
      QueryParameterSetter.QueryMetadata metadata,
      JpaParametersParameterAccessor accessor) {

    return bind(query, metadata, accessor)
        .then(
            Mono.defer(
                () -> {
                  if (!useJpaForPaging
                      || !parameters.hasLimitingParameters()
                      || accessor.getPageable().isUnpaged()) {
                    return Mono.just(query);
                  }

                  if (query instanceof Stage.SelectionQuery<?> selectionQuery) {
                    selectionQuery.setFirstResult(
                        PageableUtils.getOffsetAsInteger(accessor.getPageable()));
                    selectionQuery.setMaxResults(accessor.getPageable().getPageSize());
                  }

                  return Mono.just(query);
                }));

    //    if (!useJpaForPaging
    //        || !parameters.hasLimitingParameters()
    //        || accessor.getPageable().isUnpaged()) {
    //      return query;
    //    }

    //    if (query instanceof Stage.SelectionQuery<?> selectionQuery) {
    //      selectionQuery.setFirstResult(PageableUtils.getOffsetAsInteger(accessor.getPageable()));
    //      selectionQuery.setMaxResults(accessor.getPageable().getPageSize());
    //    }
    //
    //    return query;
  }
}
