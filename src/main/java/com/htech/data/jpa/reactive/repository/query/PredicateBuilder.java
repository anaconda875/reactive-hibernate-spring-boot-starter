package com.htech.data.jpa.reactive.repository.query;

import static com.htech.data.jpa.reactive.repository.query.QueryUtils.toExpressionRecursively;
import static org.springframework.data.repository.query.parser.Part.Type.*;

import jakarta.persistence.criteria.*;
import java.util.Collection;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.util.Assert;

@SuppressWarnings({"unchecked", "rawtypes"})
public class PredicateBuilder {

  private final ParameterMetadataProvider provider;
  private final CriteriaBuilder builder;
  private final Part part;
  private final Root<?> root;
  private final EscapeCharacter escape;

  public PredicateBuilder(
      ParameterMetadataProvider provider,
      CriteriaBuilder builder,
      Part part,
      Root<?> root,
      EscapeCharacter escape) {
    this.provider = provider;
    this.builder = builder;
    this.part = part;
    this.root = root;
    this.escape = escape;
  }

  public Predicate build() {
    PropertyPath property = part.getProperty();
    Part.Type type = part.getType();

    switch (type) {
      case BETWEEN:
        ParameterMetadataProvider.ParameterMetadata<Comparable> first = provider.next(part);
        ParameterMetadataProvider.ParameterMetadata<Comparable> second = provider.next(part);
        return builder.between(
            getComparablePath(root, part), first.getExpression(), second.getExpression());
      case AFTER:
      case GREATER_THAN:
        return builder.greaterThan(
            getComparablePath(root, part), provider.next(part, Comparable.class).getExpression());
      case GREATER_THAN_EQUAL:
        return builder.greaterThanOrEqualTo(
            getComparablePath(root, part), provider.next(part, Comparable.class).getExpression());
      case BEFORE:
      case LESS_THAN:
        return builder.lessThan(
            getComparablePath(root, part), provider.next(part, Comparable.class).getExpression());
      case LESS_THAN_EQUAL:
        return builder.lessThanOrEqualTo(
            getComparablePath(root, part), provider.next(part, Comparable.class).getExpression());
      case IS_NULL:
        return getTypedPath(root, part).isNull();
      case IS_NOT_NULL:
        return getTypedPath(root, part).isNotNull();
      case NOT_IN:
        // cast required for eclipselink workaround, see DATAJPA-433
        return upperIfIgnoreCase(getTypedPath(root, part))
            .in((Expression<Collection<?>>) provider.next(part, Collection.class).getExpression())
            .not();
      case IN:
        // cast required for eclipselink workaround, see DATAJPA-433
        return upperIfIgnoreCase(getTypedPath(root, part))
            .in((Expression<Collection<?>>) provider.next(part, Collection.class).getExpression());
      case STARTING_WITH:
      case ENDING_WITH:
      case CONTAINING:
      case NOT_CONTAINING:
        if (property.getLeafProperty().isCollection()) {

          Expression<Collection<Object>> propertyExpression = traversePath(root, property);
          ParameterExpression<Object> parameterExpression = provider.next(part).getExpression();

          // Can't just call .not() in case of negation as EclipseLink chokes on that.
          return type.equals(NOT_CONTAINING)
              ? isNotMember(builder, parameterExpression, propertyExpression)
              : isMember(builder, parameterExpression, propertyExpression);
        }

      case LIKE:
      case NOT_LIKE:
        Expression<String> stringPath = getTypedPath(root, part);
        Expression<String> propertyExpression = upperIfIgnoreCase(stringPath);
        Expression<String> parameterExpression =
            upperIfIgnoreCase(provider.next(part, String.class).getExpression());
        Predicate like =
            builder.like(propertyExpression, parameterExpression, escape.getEscapeCharacter());
        return type.equals(NOT_LIKE) || type.equals(NOT_CONTAINING) ? like.not() : like;
      case TRUE:
        Expression<Boolean> truePath = getTypedPath(root, part);
        return builder.isTrue(truePath);
      case FALSE:
        Expression<Boolean> falsePath = getTypedPath(root, part);
        return builder.isFalse(falsePath);
      case SIMPLE_PROPERTY:
        ParameterMetadataProvider.ParameterMetadata<Object> expression = provider.next(part);
        Expression<Object> path = getTypedPath(root, part);
        return expression.isIsNullParameter()
            ? path.isNull()
            : builder.equal(upperIfIgnoreCase(path), upperIfIgnoreCase(expression.getExpression()));
      case NEGATING_SIMPLE_PROPERTY:
        return builder.notEqual(
            upperIfIgnoreCase(getTypedPath(root, part)),
            upperIfIgnoreCase(provider.next(part).getExpression()));
      case IS_EMPTY:
      case IS_NOT_EMPTY:
        if (!property.getLeafProperty().isCollection()) {
          throw new IllegalArgumentException(
              "IsEmpty / IsNotEmpty can only be used on collection properties");
        }

        Expression<Collection<Object>> collectionPath = traversePath(root, property);
        return type.equals(IS_NOT_EMPTY)
            ? builder.isNotEmpty(collectionPath)
            : builder.isEmpty(collectionPath);

      default:
        throw new IllegalArgumentException("Unsupported keyword " + type);
    }
  }

  private <T> Predicate isMember(
      CriteriaBuilder builder, Expression<T> parameter, Expression<Collection<T>> property) {
    return builder.isMember(parameter, property);
  }

  private <T> Predicate isNotMember(
      CriteriaBuilder builder, Expression<T> parameter, Expression<Collection<T>> property) {
    return builder.isNotMember(parameter, property);
  }

  private <T> Expression<T> upperIfIgnoreCase(Expression<? extends T> expression) {
    switch (part.shouldIgnoreCase()) {
      case ALWAYS:
        Assert.state(
            canUpperCase(expression),
            "Unable to ignore case of "
                + expression.getJavaType().getName()
                + " types, the property '"
                + part.getProperty().getSegment()
                + "' must reference a String");
        return (Expression<T>) builder.upper((Expression<String>) expression);

      case WHEN_POSSIBLE:
        if (canUpperCase(expression)) {
          return (Expression<T>) builder.upper((Expression<String>) expression);
        }

      case NEVER:
      default:
        return (Expression<T>) expression;
    }
  }

  private boolean canUpperCase(Expression<?> expression) {
    return String.class.equals(expression.getJavaType());
  }

  private Expression<? extends Comparable> getComparablePath(Root<?> root, Part part) {
    return getTypedPath(root, part);
  }

  private <T> Expression<T> getTypedPath(Root<?> root, Part part) {
    return toExpressionRecursively(root, part.getProperty());
  }

  private <T> Expression<T> traversePath(Path<?> root, PropertyPath path) {
    Path<Object> result = root.get(path.getSegment());
    return (Expression<T>) (path.hasNext() ? traversePath(result, path.next()) : result);
  }
}
