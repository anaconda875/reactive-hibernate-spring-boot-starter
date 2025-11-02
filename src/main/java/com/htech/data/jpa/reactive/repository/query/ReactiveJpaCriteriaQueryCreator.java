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

    return (typeToRead == null) || tree.isExistsProjection()
        ? builder.createTupleQuery()
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
          typeToRead.isInterface()
              ? query.multiselect(selections)
              : query.select(
                  (Selection) builder.construct(typeToRead, selections.toArray(new Selection[0])));

    } else if (tree.isExistsProjection()) {
      if (root.getModel().hasSingleIdAttribute()) {
        SingularAttribute<?, ?> id =
            root.getModel().getId(root.getModel().getIdType().getJavaType());
        query = query.multiselect(root.get((SingularAttribute) id).alias(id.getName()));

      } else {
        query =
            query.multiselect(
                root.getModel().getIdClassAttributes().stream()
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
    return new PredicateBuilder(provider, builder, part, root, escape).build();
  }
}
