package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.ParameterExpression;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.expression.Expression;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

public class ParameterMetadataProvider {

  private final CriteriaBuilder builder;
  private final Iterator<? extends Parameter> parameters;
  private final List<ParameterMetadataProvider.ParameterMetadata<?>> expressions;
  private final @Nullable Iterator<Object> bindableParameterValues;
  private final EscapeCharacter escape;

  public ParameterMetadataProvider(
      CriteriaBuilder builder, ParametersParameterAccessor accessor, EscapeCharacter escape) {
    this(builder, accessor.iterator(), accessor.getParameters(), escape);
  }

  public ParameterMetadataProvider(
      CriteriaBuilder builder, Parameters<?, ?> parameters, EscapeCharacter escape) {
    this(builder, null, parameters, escape);
  }

  private ParameterMetadataProvider(
      CriteriaBuilder builder,
      @Nullable Iterator<Object> bindableParameterValues,
      Parameters<?, ?> parameters,
      EscapeCharacter escape) {

    Assert.notNull(builder, "CriteriaBuilder must not be null");
    Assert.notNull(parameters, "Parameters must not be null");
    Assert.notNull(escape, "EscapeCharacter must not be null");

    this.builder = builder;
    this.parameters = parameters.getBindableParameters().iterator();
    this.expressions = new ArrayList<>();
    this.bindableParameterValues = bindableParameterValues;
    this.escape = escape;
  }

  public List<ParameterMetadataProvider.ParameterMetadata<?>> getExpressions() {
    return expressions;
  }

  @SuppressWarnings("unchecked")
  public <T> ParameterMetadataProvider.ParameterMetadata<T> next(Part part) {

    Assert.isTrue(
        parameters.hasNext(), () -> String.format("No parameter available for part %s", part));

    Parameter parameter = parameters.next();
    return (ParameterMetadataProvider.ParameterMetadata<T>)
        next(part, parameter.getType(), parameter);
  }

  @SuppressWarnings("unchecked")
  public <T> ParameterMetadataProvider.ParameterMetadata<? extends T> next(
      Part part, Class<T> type) {

    Parameter parameter = parameters.next();
    Class<?> typeToUse =
        ClassUtils.isAssignable(type, parameter.getType()) ? parameter.getType() : type;
    return (ParameterMetadataProvider.ParameterMetadata<? extends T>)
        next(part, typeToUse, parameter);
  }

  private <T> ParameterMetadataProvider.ParameterMetadata<T> next(
      Part part, Class<T> type, Parameter parameter) {

    Assert.notNull(type, "Type must not be null");

    /*
     * We treat Expression types as Object vales since the real value to be bound as a parameter is determined at query time.
     */
    @SuppressWarnings("unchecked")
    Class<T> reifiedType = Expression.class.equals(type) ? (Class<T>) Object.class : type;

    Supplier<String> name =
        () ->
            parameter
                .getName()
                .orElseThrow(() -> new IllegalArgumentException("o_O Parameter needs to be named"));

    ParameterExpression<T> expression =
        parameter.isExplicitlyNamed() //
            ? builder.parameter(reifiedType, name.get()) //
            : builder.parameter(reifiedType);

    Object value =
        bindableParameterValues == null
            ? ParameterMetadataProvider.ParameterMetadata.PLACEHOLDER
            : bindableParameterValues.next();

    ParameterMetadataProvider.ParameterMetadata<T> metadata =
        new ParameterMetadataProvider.ParameterMetadata<>(expression, part, value, escape);
    expressions.add(metadata);

    return metadata;
  }

  EscapeCharacter getEscape() {
    return escape;
  }

  static class ParameterMetadata<T> {

    static final Object PLACEHOLDER = new Object();

    private final Part.Type type;
    private final ParameterExpression<T> expression;
    private final EscapeCharacter escape;
    private final boolean ignoreCase;
    private final boolean noWildcards;

    public ParameterMetadata(
        ParameterExpression<T> expression,
        Part part,
        @Nullable Object value,
        EscapeCharacter escape) {

      this.expression = expression;
      this.type =
          value == null && Part.Type.SIMPLE_PROPERTY.equals(part.getType())
              ? Part.Type.IS_NULL
              : part.getType();
      this.ignoreCase = Part.IgnoreCaseType.ALWAYS.equals(part.shouldIgnoreCase());
      this.noWildcards = part.getProperty().getLeafProperty().isCollection();
      this.escape = escape;
    }

    public ParameterExpression<T> getExpression() {
      return expression;
    }

    public boolean isIsNullParameter() {
      return Part.Type.IS_NULL.equals(type);
    }

    @Nullable
    public Object prepare(@Nullable Object value) {

      if (value == null || expression.getJavaType() == null) {
        return value;
      }

      if (String.class.equals(expression.getJavaType()) && !noWildcards) {

        switch (type) {
          case STARTING_WITH:
            return String.format("%s%%", escape.escape(value.toString()));
          case ENDING_WITH:
            return String.format("%%%s", escape.escape(value.toString()));
          case CONTAINING:
          case NOT_CONTAINING:
            return String.format("%%%s%%", escape.escape(value.toString()));
          default:
            return value;
        }
      }

      return Collection.class.isAssignableFrom(expression.getJavaType()) //
          ? upperIfIgnoreCase(ignoreCase, toCollection(value)) //
          : value;
    }

    @Nullable
    private static Collection<?> toCollection(@Nullable Object value) {

      if (value == null) {
        return null;
      }

      if (value instanceof Collection<?> collection) {
        return collection.isEmpty() ? null : collection;
      }

      if (ObjectUtils.isArray(value)) {

        List<Object> collection = Arrays.asList(ObjectUtils.toObjectArray(value));
        return collection.isEmpty() ? null : collection;
      }

      return Collections.singleton(value);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Collection<?> upperIfIgnoreCase(
        boolean ignoreCase, @Nullable Collection<?> collection) {

      if (!ignoreCase || CollectionUtils.isEmpty(collection)) {
        return collection;
      }

      return ((Collection<String>) collection)
          .stream() //
              .map(
                  it ->
                      it == null //
                          ? null //
                          : it.toUpperCase()) //
              .collect(Collectors.toList());
    }
  }
}
