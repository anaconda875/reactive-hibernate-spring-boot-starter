package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.lang.Nullable;

/**
 * @author Bao.Ngo
 */
public class ReactiveJpaCountQueryCreator extends ReactiveJpaCriteriaQueryCreator {

  protected final boolean distinct;

  public ReactiveJpaCountQueryCreator(
      PartTree tree,
      ReturnedType type,
      CriteriaBuilder builder,
      ParameterMetadataProvider provider) {
    super(tree, type, builder, provider);
    distinct = tree.isDistinct();
  }

  @Override
  protected CriteriaQuery<? extends Object> createCriteriaQuery(
      CriteriaBuilder builder, ReturnedType type) {
    return builder.createQuery(Long.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected CriteriaQuery<? extends Object> complete(
      @Nullable Predicate predicate,
      Sort sort,
      CriteriaQuery<? extends Object> query,
      CriteriaBuilder builder,
      Root<?> root) {

    CriteriaQuery<? extends Object> select = query.select(getCountQuery(query, builder, root));
    return predicate == null ? select : select.where(predicate);
  }

  @SuppressWarnings("rawtypes")
  private Expression getCountQuery(CriteriaQuery<?> query, CriteriaBuilder builder, Root<?> root) {
    return distinct ? builder.countDistinct(root) : builder.count(root);
  }
}
