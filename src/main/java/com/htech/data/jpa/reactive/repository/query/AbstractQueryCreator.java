package com.htech.data.jpa.reactive.repository.query;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * @author Bao.Ngo
 */
public abstract class AbstractQueryCreator<T, S> {

  protected final Optional<ParameterAccessor> parameters;
  protected final PartTree tree;

  public AbstractQueryCreator(PartTree tree) {
    this(tree, Optional.empty());
  }

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

  public T createQuery() {
    return createQuery(
        parameters
            .map(ParameterAccessor::getSort) //
            .orElse(Sort.unsorted()));
  }

  public T createQuery(Sort dynamicSort) {
    Assert.notNull(dynamicSort, "DynamicSort must not be null");
    return complete(createCriteria(tree), tree.getSort().and(dynamicSort));
  }

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

  protected abstract S create(Part part, Iterator<Object> iterator);

  protected abstract S and(Part part, S base, Iterator<Object> iterator);

  protected abstract S or(S base, S criteria);

  protected abstract T complete(@Nullable S criteria, Sort sort);
}
