package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.ParameterExpression;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.data.jpa.provider.PersistenceProvider;
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

  /**
   * Creates a new {@link ParameterMetadataProvider} from the given {@link CriteriaBuilder} and
   * {@link ParametersParameterAccessor}.
   *
   * @param builder must not be {@literal null}.
   * @param accessor must not be {@literal null}.
   * @param escape must not be {@literal null}.
   */
  public ParameterMetadataProvider(
      CriteriaBuilder builder, ParametersParameterAccessor accessor, EscapeCharacter escape) {
    this(builder, accessor.iterator(), accessor.getParameters(), escape);
  }

  /**
   * Creates a new {@link ParameterMetadataProvider} from the given {@link CriteriaBuilder} and
   * {@link Parameters} with support for parameter value customizations via {@link
   * PersistenceProvider}.
   *
   * @param builder must not be {@literal null}.
   * @param parameters must not be {@literal null}.
   * @param escape must not be {@literal null}.
   */
  public ParameterMetadataProvider(
      CriteriaBuilder builder, Parameters<?, ?> parameters, EscapeCharacter escape) {
    this(builder, null, parameters, escape);
  }

  /**
   * Creates a new {@link ParameterMetadataProvider} from the given {@link CriteriaBuilder} an
   * {@link Iterable} of all bindable parameter values, and {@link Parameters}.
   *
   * @param builder must not be {@literal null}.
   * @param bindableParameterValues may be {@literal null}.
   * @param parameters must not be {@literal null}.
   * @param escape must not be {@literal null}.
   */
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

  /**
   * Returns all {@link ParameterMetadataProvider.ParameterMetadata}s built.
   *
   * @return the expressions
   */
  public List<ParameterMetadataProvider.ParameterMetadata<?>> getExpressions() {
    return expressions;
  }

  /**
   * Builds a new {@link ParameterMetadataProvider.ParameterMetadata} for given {@link Part} and the
   * next {@link Parameter}.
   */
  @SuppressWarnings("unchecked")
  public <T> ParameterMetadataProvider.ParameterMetadata<T> next(Part part) {

    Assert.isTrue(
        parameters.hasNext(), () -> String.format("No parameter available for part %s", part));

    Parameter parameter = parameters.next();
    return (ParameterMetadataProvider.ParameterMetadata<T>)
        next(part, parameter.getType(), parameter);
  }

  /**
   * Builds a new {@link ParameterMetadataProvider.ParameterMetadata} of the given {@link Part} and
   * type. Forwards the underlying {@link Parameters} as well.
   *
   * @param <T> is the type parameter of the returned {@link
   *     ParameterMetadataProvider.ParameterMetadata}.
   * @param type must not be {@literal null}.
   * @return ParameterMetadata for the next parameter.
   */
  @SuppressWarnings("unchecked")
  public <T> ParameterMetadataProvider.ParameterMetadata<? extends T> next(
      Part part, Class<T> type) {

    Parameter parameter = parameters.next();
    Class<?> typeToUse =
        ClassUtils.isAssignable(type, parameter.getType()) ? parameter.getType() : type;
    return (ParameterMetadataProvider.ParameterMetadata<? extends T>)
        next(part, typeToUse, parameter);
  }

  /**
   * Builds a new {@link ParameterMetadataProvider.ParameterMetadata} for the given type and name.
   *
   * @param <T> type parameter for the returned {@link ParameterMetadataProvider.ParameterMetadata}.
   * @param part must not be {@literal null}.
   * @param type must not be {@literal null}.
   * @param parameter providing the name for the returned {@link
   *     ParameterMetadataProvider.ParameterMetadata}.
   * @return a new {@link ParameterMetadataProvider.ParameterMetadata} for the given type and name.
   */
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

  /**
   * @author Oliver Gierke
   * @author Thomas Darimont
   * @author Andrey Kovalev
   * @param <T>
   */
  static class ParameterMetadata<T> {

    static final Object PLACEHOLDER = new Object();

    private final Part.Type type;
    private final ParameterExpression<T> expression;
    private final EscapeCharacter escape;
    private final boolean ignoreCase;
    private final boolean noWildcards;

    /** Creates a new {@link ParameterMetadataProvider.ParameterMetadata}. */
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

    /**
     * Returns the {@link ParameterExpression}.
     *
     * @return the expression
     */
    public ParameterExpression<T> getExpression() {
      return expression;
    }

    /** Returns whether the parameter shall be considered an {@literal IS NULL} parameter. */
    public boolean isIsNullParameter() {
      return Part.Type.IS_NULL.equals(type);
    }

    /**
     * Prepares the object before it's actually bound to the {@link jakarta.persistence.Query;}.
     *
     * @param value can be {@literal null}.
     */
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

    /**
     * Returns the given argument as {@link Collection} which means it will return it as is if it's
     * a {@link Collections}, turn an array into an {@link ArrayList} or simply wrap any other value
     * into a single element {@link Collections}.
     *
     * @param value the value to be converted to a {@link Collection}.
     * @return the object itself as a {@link Collection} or a {@link Collection} constructed from
     *     the value.
     */
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
