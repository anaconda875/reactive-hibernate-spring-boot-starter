package com.htech.data.jpa.reactive.repository.query;

import static com.htech.data.jpa.reactive.repository.query.QueryUtils.toExpressionRecursively;
import static org.springframework.data.repository.query.parser.Part.Type.*;

import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * @author Bao.Ngo
 */
public class ReactiveJpaCriteriaQueryCreator
    extends AbstractQueryCreator<CriteriaQuery<? extends Object>, Predicate> {

  protected final CriteriaBuilder builder;
  protected final Root<?> root;
  protected final CriteriaQuery<? extends Object> query;
  protected final ParameterMetadataProvider provider;
  protected final ReturnedType returnedType;
  protected final PartTree tree;
  protected final EscapeCharacter escape;

  public ReactiveJpaCriteriaQueryCreator(
      PartTree tree,
      ReturnedType type,
      CriteriaBuilder builder,
      ParameterMetadataProvider provider) {

    super(tree);
    this.tree = tree;

    CriteriaQuery<?> criteriaQuery = createCriteriaQuery(builder, type);

    this.builder = builder;
    this.query = criteriaQuery.distinct(tree.isDistinct() && !tree.isCountProjection());
    this.root = query.from(type.getDomainType());
    this.provider = provider;
    this.returnedType = type;
    this.escape = provider.getEscape();
  }

  protected CriteriaQuery<? extends Object> createCriteriaQuery(
      CriteriaBuilder builder, ReturnedType type) {

    Class<?> typeToRead = tree.isDelete() ? type.getDomainType() : type.getTypeToRead();

    return (typeToRead == null) || tree.isExistsProjection() //
        ? builder.createTupleQuery() //
        : builder.createQuery(typeToRead);
  }

  public List<ParameterMetadataProvider.ParameterMetadata<?>> getParameterExpressions() {
    return provider.getExpressions();
  }

  @Override
  protected Predicate create(Part part, Iterator<Object> iterator) {
    return toPredicate(part, root);
  }

  @Override
  protected Predicate and(Part part, Predicate base, Iterator<Object> iterator) {
    return builder.and(base, toPredicate(part, root));
  }

  @Override
  protected Predicate or(Predicate base, Predicate predicate) {
    return builder.or(base, predicate);
  }

  @Override
  protected final CriteriaQuery<? extends Object> complete(Predicate predicate, Sort sort) {
    return complete(predicate, sort, query, builder, root);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected CriteriaQuery<? extends Object> complete(
      @Nullable Predicate predicate,
      Sort sort,
      CriteriaQuery<? extends Object> query,
      CriteriaBuilder builder,
      Root<?> root) {

    if (returnedType.needsCustomConstruction()) {

      Collection<String> requiredSelection = getRequiredSelection(sort, returnedType);
      List<Selection<?>> selections = new ArrayList<>();

      for (String property : requiredSelection) {

        PropertyPath path = PropertyPath.from(property, returnedType.getDomainType());
        selections.add(toExpressionRecursively(root, path, true).alias(property));
      }

      Class<?> typeToRead = returnedType.getReturnedType();

      query =
          typeToRead.isInterface() //
              ? query.multiselect(selections) //
              : query.select(
                  (Selection)
                      builder.construct(
                          typeToRead, //
                          selections.toArray(new Selection[0])));

    } else if (tree.isExistsProjection()) {

      if (root.getModel().hasSingleIdAttribute()) {

        SingularAttribute<?, ?> id =
            root.getModel().getId(root.getModel().getIdType().getJavaType());
        query = query.multiselect(root.get((SingularAttribute) id).alias(id.getName()));

      } else {

        query =
            query.multiselect(
                root.getModel().getIdClassAttributes().stream() //
                    .map(it -> (Selection<?>) root.get((SingularAttribute) it).alias(it.getName()))
                    .collect(Collectors.toList()));
      }

    } else {
      query = query.select((Root) root);
    }

    CriteriaQuery<? extends Object> select =
        query.orderBy(QueryUtils.toOrders(sort, root, builder));
    return predicate == null ? select : select.where(predicate);
  }

  Collection<String> getRequiredSelection(Sort sort, ReturnedType returnedType) {
    return returnedType.getInputProperties();
  }

  private Predicate toPredicate(Part part, Root<?> root) {
    return new PredicateBuilder(part, root).build();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private class PredicateBuilder {

    private final Part part;
    private final Root<?> root;

    public PredicateBuilder(Part part, Root<?> root) {

      Assert.notNull(part, "Part must not be null");
      Assert.notNull(root, "Root must not be null");
      this.part = part;
      this.root = root;
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
              .in(
                  (Expression<Collection<?>>)
                      provider.next(part, Collection.class).getExpression());
        case STARTING_WITH:
        case ENDING_WITH:
        case CONTAINING:
        case NOT_CONTAINING:
          if (property.getLeafProperty().isCollection()) {

            Expression<Collection<Object>> propertyExpression = traversePath(root, property);
            ParameterExpression<Object> parameterExpression = provider.next(part).getExpression();

            // Can't just call .not() in case of negation as EclipseLink chokes on that.
            return type.equals(NOT_CONTAINING) //
                ? isNotMember(builder, parameterExpression, propertyExpression) //
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
              : builder.equal(
                  upperIfIgnoreCase(path), upperIfIgnoreCase(expression.getExpression()));
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
}
