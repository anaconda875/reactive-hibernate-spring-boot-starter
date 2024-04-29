package com.htech.data.jpa.reactive.repository.query;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public abstract class AbstractQueryCreator<T, S> {

  protected final Optional<ParameterAccessor> parameters;
  protected final PartTree tree;

  /**
   * Creates a new {@link org.springframework.data.repository.query.parser.AbstractQueryCreator} for
   * the given {@link PartTree}. This will cause {@literal null} be handed for the {@link Iterator}
   * in the callback methods.
   *
   * @param tree must not be {@literal null}.
   * @since 2.0
   */
  public AbstractQueryCreator(PartTree tree) {
    this(tree, Optional.empty());
  }

  /**
   * Creates a new {@link org.springframework.data.repository.query.parser.AbstractQueryCreator} for
   * the given {@link PartTree} and {@link ParametersParameterAccessor}. The latter is used to hand
   * actual parameter values into the callback methods as well as to apply dynamic sorting via a
   * {@link Sort} parameter.
   *
   * @param tree must not be {@literal null}.
   * @param parameters must not be {@literal null}.
   */
  public AbstractQueryCreator(PartTree tree, ParameterAccessor parameters) {
    this(tree, Optional.of(parameters));
  }

  private AbstractQueryCreator(PartTree tree, Optional<ParameterAccessor> parameters) {

    Assert.notNull(tree, "PartTree must not be null");
    Assert.notNull(parameters, "ParameterAccessor must not be null");

    this.tree = tree;
    this.parameters = parameters;
  }

  public abstract List<ParameterMetadataProvider.ParameterMetadata<?>> getParameterExpressions();

  /**
   * Creates the actual query object.
   *
   * @return
   */
  public T createQuery() {
    return createQuery(
        parameters
            .map(ParameterAccessor::getSort) //
            .orElse(Sort.unsorted()));
  }

  /**
   * Creates the actual query object applying the given {@link Sort} parameter. Use this method in
   * case you haven't provided a {@link ParameterAccessor} in the first place but want to apply
   * dynamic sorting nevertheless.
   *
   * @param dynamicSort must not be {@literal null}.
   * @return
   */
  public T createQuery(Sort dynamicSort) {

    Assert.notNull(dynamicSort, "DynamicSort must not be null");
    return complete(createCriteria(tree), tree.getSort().and(dynamicSort));
  }

  /**
   * Actual query building logic. Traverses the {@link PartTree} and invokes callback methods to
   * delegate actual criteria creation and concatenation.
   *
   * @param tree must not be {@literal null}.
   * @return
   */
  @Nullable
  private S createCriteria(PartTree tree) {

    S base = null;
    Iterator<Object> iterator =
        parameters.map(ParameterAccessor::iterator).orElse(Collections.emptyIterator());

    for (PartTree.OrPart node : tree) {

      Iterator<Part> parts = node.iterator();

      if (!parts.hasNext()) {
        throw new IllegalStateException(String.format("No part found in PartTree %s", tree));
      }

      S criteria = create(parts.next(), iterator);

      while (parts.hasNext()) {
        criteria = and(parts.next(), criteria, iterator);
      }

      base = base == null ? criteria : or(base, criteria);
    }

    return base;
  }

  /**
   * Creates a new atomic instance of the criteria object.
   *
   * @param part must not be {@literal null}.
   * @param iterator must not be {@literal null}.
   * @return
   */
  protected abstract S create(Part part, Iterator<Object> iterator);

  /**
   * Creates a new criteria object from the given part and and-concatenates it to the given base
   * criteria.
   *
   * @param part must not be {@literal null}.
   * @param base will never be {@literal null}.
   * @param iterator must not be {@literal null}.
   * @return
   */
  protected abstract S and(Part part, S base, Iterator<Object> iterator);

  /**
   * Or-concatenates the given base criteria to the given new criteria.
   *
   * @param base must not be {@literal null}.
   * @param criteria must not be {@literal null}.
   * @return
   */
  protected abstract S or(S base, S criteria);

  /**
   * Actually creates the query object applying the given criteria object and {@link Sort}
   * definition.
   *
   * @param criteria can be {@literal null}.
   * @param sort must not be {@literal null}.
   * @return
   */
  protected abstract T complete(@Nullable S criteria, Sort sort);
}
