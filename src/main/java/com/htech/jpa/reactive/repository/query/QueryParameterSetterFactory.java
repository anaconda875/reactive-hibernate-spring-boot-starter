package com.htech.jpa.reactive.repository.query;

import jakarta.persistence.TemporalType;
import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.spel.EvaluationContextProvider;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.List;
import java.util.function.Function;

public abstract class QueryParameterSetterFactory {

  @Nullable
  abstract QueryParameterSetter create(ParameterBinding binding, DeclaredQuery declaredQuery);

  /**
   * Creates a new {@link QueryParameterSetterFactory} for the given {@link ReactiveJpaParameters}.
   *
   * @param parameters must not be {@literal null}.
   * @return a basic {@link QueryParameterSetterFactory} that can handle named and index parameters.
   */
  static QueryParameterSetterFactory basic(ReactiveJpaParameters parameters) {

    Assert.notNull(parameters, "JpaParameters must not be null");

    return new QueryParameterSetterFactory.BasicQueryParameterSetterFactory(parameters);
  }

  static QueryParameterSetterFactory forCriteriaQuery(ReactiveJpaParameters parameters, List<ParameterMetadataProvider.ParameterMetadata<?>> metadata) {

    Assert.notNull(parameters, "JpaParameters must not be null");
    Assert.notNull(metadata, "ParameterMetadata must not be null");

    return new QueryParameterSetterFactory.CriteriaQueryParameterSetterFactory(parameters, metadata);
  }

  /**
   * Creates a new {@link QueryParameterSetterFactory} for the given {@link SpelExpressionParser},
   * {@link EvaluationContextProvider} and {@link Parameters}.
   *
   * @param parser must not be {@literal null}.
   * @param evaluationContextProvider must not be {@literal null}.
   * @param parameters must not be {@literal null}.
   * @return a {@link QueryParameterSetterFactory} that can handle
   *         {@link org.springframework.expression.spel.standard.SpelExpression}s.
   */
  static QueryParameterSetterFactory parsing(SpelExpressionParser parser, QueryMethodEvaluationContextProvider evaluationContextProvider, Parameters<?, ?> parameters) {

    Assert.notNull(parser, "SpelExpressionParser must not be null");
    Assert.notNull(evaluationContextProvider, "EvaluationContextProvider must not be null");
    Assert.notNull(parameters, "Parameters must not be null");

    return new QueryParameterSetterFactory.ExpressionBasedQueryParameterSetterFactory(parser, evaluationContextProvider, parameters);
  }

  /**
   * Creates a {@link QueryParameterSetter} from a {@link ReactiveJpaParameters.JpaParameter}. Handles named and indexed parameters,
   * TemporalType annotations and might ignore certain exception when requested to do so.
   *
   * @param valueExtractor extracts the relevant value from an array of method parameter values.
   * @param binding the binding of the query parameter to be set.
   * @param parameter the method parameter to bind.
   */
  private static QueryParameterSetter createSetter(Function<JpaParametersParameterAccessor, Object> valueExtractor,
                                                                                                 ParameterBinding binding, @Nullable ReactiveJpaParameters.JpaParameter parameter) {

    TemporalType temporalType = parameter != null && parameter.isTemporalParameter() //
        ? parameter.getRequiredTemporalType() //
        : null;

    return new QueryParameterSetter.NamedOrIndexedQueryParameterSetter(valueExtractor.andThen(binding::prepare),
        QueryParameterSetterFactory.ParameterImpl.of(parameter, binding), temporalType);
  }

  @Nullable
  static ReactiveJpaParameters.JpaParameter findParameterForBinding(Parameters<ReactiveJpaParameters, ReactiveJpaParameters.JpaParameter> parameters, String name) {

    ReactiveJpaParameters bindableParameters = parameters.getBindableParameters();

    for (ReactiveJpaParameters.JpaParameter bindableParameter : bindableParameters) {
      if (name.equals(getRequiredName(bindableParameter))) {
        return bindableParameter;
      }
    }

    return null;
  }

  private static String getRequiredName(ReactiveJpaParameters.JpaParameter p) {
    return p.getName().orElseThrow(() -> new IllegalStateException(ParameterBinder.PARAMETER_NEEDS_TO_BE_NAMED));
  }

  static ReactiveJpaParameters.JpaParameter findParameterForBinding(Parameters<ReactiveJpaParameters, ReactiveJpaParameters.JpaParameter> parameters, int parameterIndex) {

    ReactiveJpaParameters bindableParameters = parameters.getBindableParameters();

    Assert.isTrue( //
        parameterIndex < bindableParameters.getNumberOfParameters(), //
        () -> String.format( //
            "At least %s parameter(s) provided but only %s parameter(s) present in query", //
            parameterIndex + 1, //
            bindableParameters.getNumberOfParameters() //
        ) //
    );

    return bindableParameters.getParameter(parameterIndex);
  }

  /**
   * Handles bindings that are SpEL expressions by evaluating the expression to obtain a value.
   *
   * @author Jens Schauder
   * @author Oliver Gierke
   * @since 2.0
   */
  private static class ExpressionBasedQueryParameterSetterFactory extends QueryParameterSetterFactory {

    private final SpelExpressionParser parser;
    private final QueryMethodEvaluationContextProvider evaluationContextProvider;
    private final Parameters<?, ?> parameters;

    /**
     * @param parser must not be {@literal null}.
     * @param evaluationContextProvider must not be {@literal null}.
     * @param parameters must not be {@literal null}.
     */
    ExpressionBasedQueryParameterSetterFactory(SpelExpressionParser parser,
                                               QueryMethodEvaluationContextProvider evaluationContextProvider, Parameters<?, ?> parameters) {

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

      if (!(binding.getOrigin()instanceof ParameterBinding.Expression e)) {
        return null;
      }

      Expression expression = parser.parseExpression(e.expression());

      return createSetter(values -> evaluateExpression(expression, values), binding, null);
    }

    /**
     * Evaluates the given {@link Expression} against the given values.
     *
     * @param expression must not be {@literal null}.
     * @param accessor must not be {@literal null}.
     * @return the result of the evaluation.
     */
    @Nullable
    private Object evaluateExpression(Expression expression, JpaParametersParameterAccessor accessor) {

      EvaluationContext context = evaluationContextProvider.getEvaluationContext(parameters, accessor.getValues());

      return expression.getValue(context, Object.class);
    }
  }

  /**
   * Extracts values for parameter bindings from method parameters. It handles named as well as indexed parameters.
   *
   * @author Jens Schauder
   * @author Oliver Gierke
   * @author Mark Paluch
   * @since 2.0
   */
  private static class BasicQueryParameterSetterFactory extends QueryParameterSetterFactory {

    private final ReactiveJpaParameters parameters;

    /**
     * @param parameters must not be {@literal null}.
     */
    BasicQueryParameterSetterFactory(ReactiveJpaParameters parameters) {

      Assert.notNull(parameters, "JpaParameters must not be null");

      this.parameters = parameters;
    }

    @Override
    public QueryParameterSetter create(ParameterBinding binding, DeclaredQuery declaredQuery) {

      Assert.notNull(binding, "Binding must not be null");

      ReactiveJpaParameters.JpaParameter parameter;
      if (!(binding.getOrigin()instanceof ParameterBinding.MethodInvocationArgument mia)) {
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
          : createSetter(values -> getValue(values, parameter), binding, parameter);
    }

    @Nullable
    private Object getValue(JpaParametersParameterAccessor accessor, Parameter parameter) {
      return accessor.getValue(parameter);
    }
  }

  /**
   * @author Jens Schauder
   * @author Oliver Gierke
   * @see QueryParameterSetterFactory
   */
  private static class CriteriaQueryParameterSetterFactory extends QueryParameterSetterFactory {

    private final ReactiveJpaParameters parameters;
    private final List<ParameterMetadataProvider.ParameterMetadata<?>> parameterMetadata;

    /**
     * Creates a new {@link QueryParameterSetterFactory} from the given {@link ReactiveJpaParameters} and
     * {@link ParameterMetadataProvider.ParameterMetadata}.
     *
     * @param parameters must not be {@literal null}.
     * @param metadata must not be {@literal null}.
     */
    CriteriaQueryParameterSetterFactory(ReactiveJpaParameters parameters, List<ParameterMetadataProvider.ParameterMetadata<?>> metadata) {

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
          () -> String.format( //
              "At least %s parameter(s) provided but only %s parameter(s) present in query", //
              binding.getRequiredPosition(), //
              parameterMetadata.size() //
          ) //
      );

      ParameterMetadataProvider.ParameterMetadata<?> metadata = parameterMetadata.get(parameterIndex);

      if (metadata.isIsNullParameter()) {
        return QueryParameterSetter.NOOP;
      }

      ReactiveJpaParameters.JpaParameter parameter = parameters.getBindableParameter(parameterIndex);
      TemporalType temporalType = parameter.isTemporalParameter() ? parameter.getRequiredTemporalType() : null;

      return new QueryParameterSetter.NamedOrIndexedQueryParameterSetter(values -> getAndPrepare(parameter, metadata, values),
          metadata.getExpression(), temporalType);
    }

    @Nullable
    private Object getAndPrepare(ReactiveJpaParameters.JpaParameter parameter, ParameterMetadataProvider.ParameterMetadata<?> metadata,
                                 JpaParametersParameterAccessor accessor) {
      return metadata.prepare(accessor.getValue(parameter));
    }
  }

  static class ParameterImpl<T> implements jakarta.persistence.Parameter<T> {

    private final ParameterBinding.BindingIdentifier identifier;
    private final Class<T> parameterType;

    /**
     * Creates a new {@link QueryParameterSetterFactory.ParameterImpl} for the given {@link ReactiveJpaParameters.JpaParameter} and {@link ParameterBinding}.
     *
     * @param parameter can be {@literal null}.
     * @param binding must not be {@literal null}.
     * @return a {@link jakarta.persistence.Parameter} object based on the information from the arguments.
     */
    static jakarta.persistence.Parameter<?> of(@Nullable ReactiveJpaParameters.JpaParameter parameter, ParameterBinding binding) {

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
