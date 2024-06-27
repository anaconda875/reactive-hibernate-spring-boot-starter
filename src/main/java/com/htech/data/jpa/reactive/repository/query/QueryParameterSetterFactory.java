package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.TemporalType;
import java.util.List;
import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.ReactiveQueryMethodEvaluationContextProvider;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public abstract class QueryParameterSetterFactory {

  @Nullable
  abstract QueryParameterSetter create(ParameterBinding binding, DeclaredQuery declaredQuery);

  static QueryParameterSetterFactory basic(ReactiveJpaParameters parameters) {
    Assert.notNull(parameters, "JpaParameters must not be null");

    return new QueryParameterSetterFactory.BasicQueryParameterSetterFactory(parameters);
  }

  static QueryParameterSetterFactory forCriteriaQuery(
      ReactiveJpaParameters parameters,
      List<ParameterMetadataProvider.ParameterMetadata<?>> metadata) {

    Assert.notNull(parameters, "JpaParameters must not be null");
    Assert.notNull(metadata, "ParameterMetadata must not be null");

    return new QueryParameterSetterFactory.CriteriaQueryParameterSetterFactory(
        parameters, metadata);
  }

  static QueryParameterSetterFactory parsing(
      SpelExpressionParser parser,
      ReactiveQueryMethodEvaluationContextProvider evaluationContextProvider,
      Parameters<?, ?> parameters) {
    Assert.notNull(parser, "SpelExpressionParser must not be null");
    Assert.notNull(evaluationContextProvider, "EvaluationContextProvider must not be null");
    Assert.notNull(parameters, "Parameters must not be null");

    return new QueryParameterSetterFactory.ExpressionBasedQueryParameterSetterFactory(
        parser, evaluationContextProvider, parameters);
  }

  private static QueryParameterSetter createSetter(
      ParameterValueEvaluator valueEvaluator,
      //      Function<JpaParametersParameterAccessor, Mono<Object>> valueExtractor,
      ParameterBinding binding,
      @Nullable ReactiveJpaParameters.JpaParameter parameter) {

    TemporalType temporalType =
        parameter != null && parameter.isTemporalParameter() //
            ? parameter.getRequiredTemporalType() //
            : null;

    return new QueryParameterSetter.NamedOrIndexedQueryParameterSetter(
        valueEvaluator,
        binding,
        //        valueExtractor.andThen(binding::prepare),
        QueryParameterSetterFactory.ParameterImpl.of(parameter, binding),
        temporalType);
  }

  @Nullable
  static ReactiveJpaParameters.JpaParameter findParameterForBinding(
      Parameters<ReactiveJpaParameters, ReactiveJpaParameters.JpaParameter> parameters,
      String name) {

    ReactiveJpaParameters bindableParameters = parameters.getBindableParameters();

    for (ReactiveJpaParameters.JpaParameter bindableParameter : bindableParameters) {
      if (name.equals(getRequiredName(bindableParameter))) {
        return bindableParameter;
      }
    }

    return null;
  }

  private static String getRequiredName(ReactiveJpaParameters.JpaParameter p) {
    return p.getName()
        .orElseThrow(() -> new IllegalStateException(ParameterBinder.PARAMETER_NEEDS_TO_BE_NAMED));
  }

  static ReactiveJpaParameters.JpaParameter findParameterForBinding(
      Parameters<ReactiveJpaParameters, ReactiveJpaParameters.JpaParameter> parameters,
      int parameterIndex) {
    ReactiveJpaParameters bindableParameters = parameters.getBindableParameters();

    Assert.isTrue( //
        parameterIndex < bindableParameters.getNumberOfParameters(), //
        () ->
            String.format( //
                "At least %s parameter(s) provided but only %s parameter(s) present in query", //
                parameterIndex + 1, //
                bindableParameters.getNumberOfParameters() //
                ) //
        );

    return bindableParameters.getParameter(parameterIndex);
  }

  private static class ExpressionBasedQueryParameterSetterFactory
      extends QueryParameterSetterFactory {

    private final SpelExpressionParser parser;
    private final ReactiveQueryMethodEvaluationContextProvider evaluationContextProvider;
    private final Parameters<?, ?> parameters;

    ExpressionBasedQueryParameterSetterFactory(
        SpelExpressionParser parser,
        ReactiveQueryMethodEvaluationContextProvider evaluationContextProvider,
        Parameters<?, ?> parameters) {
      Assert.notNull(evaluationContextProvider, "EvaluationContextProvider must not be null");
      Assert.notNull(parser, "SpelExpressionParser must not be null");
      Assert.notNull(parameters, "Parameters must not be null");

      this.evaluationContextProvider = evaluationContextProvider;
      this.parser = parser;
      this.parameters = parameters;
    }

    @Nullable
    @Override
    public QueryParameterSetter create(ParameterBinding binding, DeclaredQuery declaredQuery) {
      if (!(binding.getOrigin() instanceof ParameterBinding.Expression e)) {
        return null;
      }

      Expression expression = parser.parseExpression(e.expression());

      return QueryParameterSetterFactory.createSetter(
          new SpELParameterValueEvaluator(evaluationContextProvider, parameters, expression),
          binding,
          null);
      //
      //      return createSetter(values -> evaluateExpression(expression, values), binding, null);

      //      if (!(binding.getOrigin() instanceof ParameterBinding.Expression e)) {
      //        return Mono.empty();
      //      }
      //
      //      return Mono.just(e.expression()).map(parser::parseExpression)
      //          .map(expression -> QueryParameterSetterFactory
      //              .createSetter(new SpELParameterValueEvaluator(evaluationContextProvider,
      // parameters, expression), binding, null));

      //      Function<JpaParametersParameterAccessor, Mono<Object>>[] valueExtractor = new
      // Function[1];
      //      return Mono.just(e.expression()).map(parser::parseExpression)
      //          .flatMap(expression -> {
      //            return Mono.<Mono<Object>>create(sink -> {
      //              valueExtractor[0] = value -> {
      //                Mono<Object> mono = evaluateExpression(expression, value);
      //                sink.success(mono);
      //                return mono;
      //              };
      //            }).flatMap(Function.identity())
      //              .map(param -> QueryParameterSetterFactory.createSetter(valueExtractor[0],
      // binding, null));
      ////            QueryParameterSetterFactory.createSetter(value ->
      // evaluateExpression(expression, value), binding, null);
      //          });
    }

    //    private Mono<Object> evaluateExpression(
    //        Expression expression, JpaParametersParameterAccessor accessor) {
    ////      EvaluationContext context =
    ////          evaluationContextProvider.getEvaluationContext(parameters, accessor.getValues());
    ////
    ////      return expression.getValue(context, Object.class);
    //      return evaluationContextProvider.getEvaluationContextLater(parameters,
    // accessor.getValues())
    //          .map(context -> expression.getValue(context, Object.class));
    //    }
  }

  private static class BasicQueryParameterSetterFactory extends QueryParameterSetterFactory {

    private final ReactiveJpaParameters parameters;

    BasicQueryParameterSetterFactory(ReactiveJpaParameters parameters) {
      Assert.notNull(parameters, "JpaParameters must not be null");

      this.parameters = parameters;
    }

    @Override
    public QueryParameterSetter create(ParameterBinding binding, DeclaredQuery declaredQuery) {
      Assert.notNull(binding, "Binding must not be null");

      ReactiveJpaParameters.JpaParameter parameter;
      if (!(binding.getOrigin() instanceof ParameterBinding.MethodInvocationArgument mia)) {
        return QueryParameterSetter.NOOP;
      }

      ParameterBinding.BindingIdentifier identifier = mia.identifier();

      if (declaredQuery.hasNamedParameter()) {
        parameter = findParameterForBinding(parameters, identifier.getName());
      } else {
        parameter = findParameterForBinding(parameters, identifier.getPosition() - 1);
      }

      return parameter == null //
          ? QueryParameterSetter.NOOP //
          : createSetter(new BasicParameterValueEvaluator(parameter), binding, parameter);
    }

    @Nullable
    private Object getValue(JpaParametersParameterAccessor accessor, Parameter parameter) {
      return accessor.getValue(parameter);
    }
  }

  private static class CriteriaQueryParameterSetterFactory extends QueryParameterSetterFactory {

    private final ReactiveJpaParameters parameters;
    private final List<ParameterMetadataProvider.ParameterMetadata<?>> parameterMetadata;

    CriteriaQueryParameterSetterFactory(
        ReactiveJpaParameters parameters,
        List<ParameterMetadataProvider.ParameterMetadata<?>> metadata) {

      Assert.notNull(parameters, "JpaParameters must not be null");
      Assert.notNull(metadata, "Expressions must not be null");

      this.parameters = parameters;
      this.parameterMetadata = metadata;
    }

    @Override
    public QueryParameterSetter create(ParameterBinding binding, DeclaredQuery declaredQuery) {

      int parameterIndex = binding.getRequiredPosition() - 1;

      Assert.isTrue( //
          parameterIndex < parameterMetadata.size(), //
          () ->
              String.format( //
                  "At least %s parameter(s) provided but only %s parameter(s) present in query", //
                  binding.getRequiredPosition(), //
                  parameterMetadata.size() //
                  ) //
          );

      ParameterMetadataProvider.ParameterMetadata<?> metadata =
          parameterMetadata.get(parameterIndex);

      if (metadata.isIsNullParameter()) {
        return QueryParameterSetter.NOOP;
      }

      ReactiveJpaParameters.JpaParameter parameter =
          parameters.getBindableParameter(parameterIndex);
      TemporalType temporalType =
          parameter.isTemporalParameter() ? parameter.getRequiredTemporalType() : null;

      return new QueryParameterSetter.NamedOrIndexedQueryParameterSetter(
          new BasicParameterValueEvaluator(parameter),
          binding,
          metadata.getExpression(),
          temporalType);
    }

    @Nullable
    private Object getAndPrepare(
        ReactiveJpaParameters.JpaParameter parameter,
        ParameterMetadataProvider.ParameterMetadata<?> metadata,
        JpaParametersParameterAccessor accessor) {
      return metadata.prepare(accessor.getValue(parameter));
    }
  }

  static class ParameterImpl<T> implements jakarta.persistence.Parameter<T> {

    private final ParameterBinding.BindingIdentifier identifier;
    private final Class<T> parameterType;

    static jakarta.persistence.Parameter<?> of(
        @Nullable ReactiveJpaParameters.JpaParameter parameter, ParameterBinding binding) {
      Class<?> type = parameter == null ? Object.class : parameter.getType();

      return new QueryParameterSetterFactory.ParameterImpl<>(binding.getIdentifier(), type);
    }

    public ParameterImpl(ParameterBinding.BindingIdentifier identifier, Class<T> parameterType) {
      this.identifier = identifier;
      this.parameterType = parameterType;
    }

    @Nullable
    @Override
    public String getName() {
      return identifier.hasName() ? identifier.getName() : null;
    }

    @Nullable
    @Override
    public Integer getPosition() {
      return identifier.hasPosition() ? identifier.getPosition() : null;
    }

    @Override
    public Class<T> getParameterType() {
      return parameterType;
    }
  }
}
