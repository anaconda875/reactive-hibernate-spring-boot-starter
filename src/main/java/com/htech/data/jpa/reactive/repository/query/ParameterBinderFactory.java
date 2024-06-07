package com.htech.data.jpa.reactive.repository.query;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.repository.query.ReactiveQueryMethodEvaluationContextProvider;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.Assert;

public class ParameterBinderFactory {

  static ParameterBinder createBinder(ReactiveJpaParameters parameters) {

    Assert.notNull(parameters, "ReactiveJpaParameters must not be null");

    QueryParameterSetterFactory setterFactory = QueryParameterSetterFactory.basic(parameters);
    List<ParameterBinding> bindings = getBindings(parameters);

    return new ParameterBinder(parameters, createSetters(bindings, setterFactory));
  }

  static ParameterBinder createCriteriaBinder(
      ReactiveJpaParameters parameters,
      List<ParameterMetadataProvider.ParameterMetadata<?>> metadata) {

    Assert.notNull(parameters, "ReactiveJpaParameters must not be null");
    Assert.notNull(metadata, "Parameter metadata must not be null");

    QueryParameterSetterFactory setterFactory =
        QueryParameterSetterFactory.forCriteriaQuery(parameters, metadata);
    List<ParameterBinding> bindings = getBindings(parameters);

    return new ParameterBinder(parameters, createSetters(bindings, setterFactory));
  }

  static ParameterBinder createQueryAwareBinder(
      ReactiveJpaParameters parameters,
      DeclaredQuery query,
      SpelExpressionParser parser,
      ReactiveQueryMethodEvaluationContextProvider evaluationContextProvider) {

    Assert.notNull(parameters, "ReactiveJpaParameters must not be null");
    Assert.notNull(query, "StringQuery must not be null");
    Assert.notNull(parser, "SpelExpressionParser must not be null");
    Assert.notNull(evaluationContextProvider, "EvaluationContextProvider must not be null");

    List<ParameterBinding> bindings = query.getParameterBindings();
    QueryParameterSetterFactory expressionSetterFactory =
        QueryParameterSetterFactory.parsing(parser, evaluationContextProvider, parameters);

    QueryParameterSetterFactory basicSetterFactory = QueryParameterSetterFactory.basic(parameters);

    return new ParameterBinder(
        parameters,
        createSetters(bindings, query, expressionSetterFactory, basicSetterFactory),
        !query.usesPaging());
  }

  private static List<ParameterBinding> getBindings(ReactiveJpaParameters parameters) {

    List<ParameterBinding> result = new ArrayList<>();
    int bindableParameterIndex = 0;

    for (ReactiveJpaParameters.JpaParameter parameter : parameters) {

      if (parameter.isBindable()) {
        int index = ++bindableParameterIndex;
        ParameterBinding.BindingIdentifier bindingIdentifier =
            parameter
                .getName()
                .map(it -> ParameterBinding.BindingIdentifier.of(it, index))
                .orElseGet(() -> ParameterBinding.BindingIdentifier.of(index));

        result.add(
            new ParameterBinding(
                bindingIdentifier,
                ParameterBinding.ParameterOrigin.ofParameter(bindingIdentifier)));
      }
    }

    return result;
  }

  private static Iterable<QueryParameterSetter> createSetters(
      List<ParameterBinding> parameterBindings, QueryParameterSetterFactory... factories) {
    return createSetters(parameterBindings, EmptyDeclaredQuery.EMPTY_QUERY, factories);
  }

  private static Iterable<QueryParameterSetter> createSetters(
      List<ParameterBinding> parameterBindings,
      DeclaredQuery declaredQuery,
      QueryParameterSetterFactory... strategies) {

    List<QueryParameterSetter> setters = new ArrayList<>(parameterBindings.size());
    for (ParameterBinding parameterBinding : parameterBindings) {
      setters.add(createQueryParameterSetter(parameterBinding, strategies, declaredQuery));
    }

    return setters;
  }

  private static QueryParameterSetter createQueryParameterSetter(
      ParameterBinding binding,
      QueryParameterSetterFactory[] strategies,
      DeclaredQuery declaredQuery) {

    for (QueryParameterSetterFactory strategy : strategies) {
      QueryParameterSetter setter = strategy.create(binding, declaredQuery);

      if (setter != null) {
        return setter;
      }
    }

    return QueryParameterSetter.NOOP;
  }
}
