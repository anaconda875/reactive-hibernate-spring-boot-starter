package com.htech.jpa.reactive.repository.query;

import jakarta.persistence.Query;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import org.springframework.data.jpa.support.PageableUtils;
import org.springframework.util.Assert;

public class ParameterBinder {

  static final String PARAMETER_NEEDS_TO_BE_NAMED = "For queries with named parameters you need to provide names for method parameters; Use @Param for query method parameters, or when on Java 8+ use the javac flag -parameters";

  private final ReactiveJpaParameters parameters;
  private final Iterable<QueryParameterSetter> parameterSetters;
  private final boolean useJpaForPaging;

  /**
   * Creates a new {@link org.springframework.data.jpa.repository.query.ParameterBinder} for the given {@link ReactiveJpaParameters} and {@link QueryParameterSetter}s.
   * Defaults to use JPA API to apply pagination offsets.
   *
   * @param parameters must not be {@literal null}.
   * @param parameterSetters must not be {@literal null}.
   * @since 2.0.6
   */
  ParameterBinder(ReactiveJpaParameters parameters, Iterable<QueryParameterSetter> parameterSetters) {
    this(parameters, parameterSetters, true);
  }

  /**
   * Creates a new {@link org.springframework.data.jpa.repository.query.ParameterBinder} for the given {@link ReactiveJpaParameters} and {@link QueryParameterSetter}s.
   *
   * @param parameters must not be {@literal null}.
   * @param parameterSetters must not be {@literal null}.
   * @param useJpaForPaging determines whether {@link Query#setFirstResult(int)} and {@link Query#setMaxResults(int)}
   *          shall be used for paging.
   */
  public ParameterBinder(ReactiveJpaParameters parameters, Iterable<QueryParameterSetter> parameterSetters,
                         boolean useJpaForPaging) {

    Assert.notNull(parameters, "ReactiveJpaParameters must not be null");
    Assert.notNull(parameterSetters, "Parameter setters must not be null");

    this.parameters = parameters;
    this.parameterSetters = parameterSetters;
    this.useJpaForPaging = useJpaForPaging;
  }

  public <T extends Mutiny.AbstractQuery> T bind(T jpaQuery, QueryParameterSetter.QueryMetadata metadata,
                                   JpaParametersParameterAccessor accessor) {

    bind(metadata.withQuery(jpaQuery), accessor, QueryParameterSetter.ErrorHandling.STRICT);
    return jpaQuery;
  }

  public void bind(QueryParameterSetter.BindableQuery query, JpaParametersParameterAccessor accessor,
                   QueryParameterSetter.ErrorHandling errorHandling) {

    for (QueryParameterSetter setter : parameterSetters) {
      setter.setParameter(query, accessor, errorHandling);
    }
  }

  /**
   * Binds the parameters to the given query and applies special parameter types (e.g. pagination).
   *
   * @param query must not be {@literal null}.
   * @param metadata must not be {@literal null}.
   * @param accessor must not be {@literal null}.
   */
  Mutiny.AbstractQuery bindAndPrepare(Mutiny.AbstractQuery query, QueryParameterSetter.QueryMetadata metadata,
                       JpaParametersParameterAccessor accessor) {

    bind(query, metadata, accessor);

    if (!useJpaForPaging || !parameters.hasLimitingParameters() || accessor.getPageable().isUnpaged()) {
      return query;
    }

    if(query instanceof Mutiny.SelectionQuery<?> selectionQuery) {
      selectionQuery.setFirstResult(PageableUtils.getOffsetAsInteger(accessor.getPageable()));
      selectionQuery.setMaxResults(accessor.getPageable().getPageSize());
    }

    return query;
  }
  
}
