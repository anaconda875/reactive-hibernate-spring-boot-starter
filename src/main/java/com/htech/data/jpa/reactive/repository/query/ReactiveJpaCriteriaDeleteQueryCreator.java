package com.htech.data.jpa.reactive.repository.query;

import static org.springframework.data.repository.query.parser.Part.Type.*;

import jakarta.persistence.criteria.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.lang.Nullable;

/**
 * @author Bao.Ngo
 */
public class ReactiveJpaCriteriaDeleteQueryCreator
    extends AbstractQueryCreator<CriteriaDelete<?>, Predicate> {

  protected final CriteriaBuilder builder;
  protected final Root<?> root;
  protected final CriteriaDelete<?> query;
  protected final ParameterMetadataProvider provider;
  protected final ReturnedType returnedType;
  protected final PartTree tree;
  protected final EscapeCharacter escape;

  public ReactiveJpaCriteriaDeleteQueryCreator(
      PartTree tree,
      ReturnedType type,
      CriteriaBuilder builder,
      ParameterMetadataProvider provider) {

    super(tree);
    this.tree = tree;

    CriteriaDelete<?> criteriaDelete = createCriteriaQuery(builder, type);

    this.builder = builder;
    this.query = criteriaDelete;
    this.root = query.from((Class) type.getDomainType());
    this.provider = provider;
    this.returnedType = type;
    this.escape = provider.getEscape();
  }

  protected CriteriaDelete<? extends Object> createCriteriaQuery(
      CriteriaBuilder builder, ReturnedType type) {
    Class<?> typeToRead = type.getDomainType();

    return builder.createCriteriaDelete(typeToRead);
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
  protected final CriteriaDelete<?> complete(Predicate predicate, Sort sort) {
    return complete(predicate, sort, query, builder, root);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected CriteriaDelete<?> complete(
      @Nullable Predicate predicate,
      Sort sort,
      CriteriaDelete<?> query,
      CriteriaBuilder builder,
      Root<?> root) {

    CriteriaDelete<?> delete = query;
    return predicate == null ? delete : delete.where(predicate);
  }

  Collection<String> getRequiredSelection(Sort sort, ReturnedType returnedType) {
    return returnedType.getInputProperties();
  }

  private Predicate toPredicate(Part part, Root<?> root) {
    return new PredicateBuilder(provider, builder, part, root, escape).build();
  }
}
