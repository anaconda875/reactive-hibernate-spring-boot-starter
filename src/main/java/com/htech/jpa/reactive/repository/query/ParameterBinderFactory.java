package com.htech.jpa.reactive.repository.query;

import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

public class ParameterBinderFactory {

  static ParameterBinder createBinder(ReactiveJpaParameters parameters) {

    Assert.notNull(parameters, "ReactiveJpaParameters must not be null");

    QueryParameterSetterFactory setterFactory = QueryParameterSetterFactory.basic(parameters);
    List<ParameterBinding> bindings = getBindings(parameters);

    return new ParameterBinder(parameters, createSetters(bindings, setterFactory));
  }

  /**
   * Creates a {@link ParameterBinder} that just matches method parameter to parameters of a
   * {@link jakarta.persistence.criteria.CriteriaQuery}.
   *
   * @param parameters method parameters that are available for binding, must not be {@literal null}.
   * @param metadata must not be {@literal null}.
   * @return a {@link ParameterBinder} that can assign values for the method parameters to query parameters of a
   *         {@link jakarta.persistence.criteria.CriteriaQuery}
   */
  static ParameterBinder createCriteriaBinder(ReactiveJpaParameters parameters, List<ParameterMetadataProvider.ParameterMetadata<?>> metadata) {

    Assert.notNull(parameters, "ReactiveJpaParameters must not be null");
    Assert.notNull(metadata, "Parameter metadata must not be null");

    QueryParameterSetterFactory setterFactory = QueryParameterSetterFactory.forCriteriaQuery(parameters, metadata);
    List<ParameterBinding> bindings = getBindings(parameters);

    return new ParameterBinder(parameters, createSetters(bindings, setterFactory));
  }

  static ParameterBinder createQueryAwareBinder(ReactiveJpaParameters parameters, DeclaredQuery query,
                                                SpelExpressionParser parser, QueryMethodEvaluationContextProvider evaluationContextProvider) {

    Assert.notNull(parameters, "ReactiveJpaParameters must not be null");
    Assert.notNull(query, "StringQuery must not be null");
    Assert.notNull(parser, "SpelExpressionParser must not be null");
    Assert.notNull(evaluationContextProvider, "EvaluationContextProvider must not be null");

    List<ParameterBinding> bindings = query.getParameterBindings();
    QueryParameterSetterFactory expressionSetterFactory = QueryParameterSetterFactory.parsing(parser,
        evaluationContextProvider, parameters);

    QueryParameterSetterFactory basicSetterFactory = QueryParameterSetterFactory.basic(parameters);

    return new ParameterBinder(parameters, createSetters(bindings, query, expressionSetterFactory, basicSetterFactory),
        !query.usesPaging());
  }

  private static List<ParameterBinding> getBindings(ReactiveJpaParameters parameters) {

    List<ParameterBinding> result = new ArrayList<>();
    int bindableParameterIndex = 0;

    for (ReactiveJpaParameters.JpaParameter parameter : parameters) {

      if (parameter.isBindable()) {
        int index = ++bindableParameterIndex;
        ParameterBinding.BindingIdentifier bindingIdentifier = parameter.getName().map(it -> ParameterBinding.BindingIdentifier.of(it, index))
            .orElseGet(() -> ParameterBinding.BindingIdentifier.of(index));

        result.add(new ParameterBinding(bindingIdentifier, ParameterBinding.ParameterOrigin.ofParameter(bindingIdentifier)));
      }
    }

    return result;
  }

  private static Iterable<QueryParameterSetter> createSetters(List<ParameterBinding> parameterBindings, QueryParameterSetterFactory... factories) {
    return createSetters(parameterBindings, EmptyDeclaredQuery.EMPTY_QUERY, factories);
  }

  private static Iterable<QueryParameterSetter> createSetters(List<ParameterBinding> parameterBindings, DeclaredQuery declaredQuery, QueryParameterSetterFactory... strategies) {

    List<QueryParameterSetter> setters = new ArrayList<>(parameterBindings.size());
    for (ParameterBinding parameterBinding : parameterBindings) {
      setters.add(createQueryParameterSetter(parameterBinding, strategies, declaredQuery));
    }

    return setters;
  }

  private static QueryParameterSetter createQueryParameterSetter(ParameterBinding binding, QueryParameterSetterFactory[] strategies, DeclaredQuery declaredQuery) {

    for (QueryParameterSetterFactory strategy : strategies) {

      QueryParameterSetter setter = strategy.create(binding, declaredQuery);

      if (setter != null) {
        return setter;
      }
    }

    return QueryParameterSetter.NOOP;
  }
}
