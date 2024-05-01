package com.htech.data.jpa.reactive.repository.query;

import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.data.jpa.repository.query.*;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.util.Lazy;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public class AbstractStringBasedReactiveJpaQuery extends AbstractReactiveJpaQuery {

  protected final DeclaredQuery query;
  protected final Lazy<DeclaredQuery> countQuery;
  protected final QueryMethodEvaluationContextProvider evaluationContextProvider;
  protected final SpelExpressionParser parser;
  protected final QueryParameterSetter.QueryMetadataCache metadataCache =
      new QueryParameterSetter.QueryMetadataCache();
  protected final QueryRewriter queryRewriter;

  public AbstractStringBasedReactiveJpaQuery(
      ReactiveJpaQueryMethod method,
      Mutiny.SessionFactory sessionFactory,
      String queryString,
      @Nullable String countQueryString,
      QueryRewriter queryRewriter,
      QueryMethodEvaluationContextProvider evaluationContextProvider,
      SpelExpressionParser parser) {

    super(method, sessionFactory);

    Assert.hasText(queryString, "Query string must not be null or empty");
    Assert.notNull(
        evaluationContextProvider, "ExpressionEvaluationContextProvider must not be null");
    Assert.notNull(parser, "Parser must not be null");
    Assert.notNull(queryRewriter, "QueryRewriter must not be null");

    this.evaluationContextProvider = evaluationContextProvider;
    this.query =
        new ExpressionBasedStringQuery(
            queryString, method.getEntityInformation(), parser, method.isNativeQuery());

    this.countQuery =
        Lazy.of(
            () -> {
              DeclaredQuery countQuery =
                  query.deriveCountQuery(countQueryString, method.getCountQueryProjection());
              return ExpressionBasedStringQuery.from(
                  countQuery, method.getEntityInformation(), parser, method.isNativeQuery());
            });

    this.parser = parser;
    this.queryRewriter = queryRewriter;

    Assert.isTrue(
        method.isNativeQuery() || !query.usesJdbcStyleParameters(),
        "JDBC style parameters (?) are not supported for JPA queries");
  }

  @Override
  public Mutiny.AbstractQuery doCreateQuery(
      ReactiveJpaParametersParameterAccessor accessor, ReactiveJpaQueryMethod method) {

    String sortedQueryString =
        QueryEnhancerFactory.forQuery(query) //
            .applySorting(accessor.getSort(), query.getAlias());
    ResultProcessor processor =
        getQueryMethod().getResultProcessor().withDynamicProjection(accessor);

    Mutiny.AbstractQuery query =
        createReactiveJpaQuery(
            sortedQueryString,
            method,
            accessor.getSession(),
            accessor.getSort(),
            accessor.getPageable(),
            processor.getReturnedType());

    QueryParameterSetter.QueryMetadata metadata =
        metadataCache.getMetadata(sortedQueryString, query);

    // it is ok to reuse the binding contained in the ParameterBinder although we create a new query
    // String because the
    // parameters in the query do not change.
    return parameterBinder.get().bindAndPrepare(query, metadata, accessor);
  }

  @Override
  protected ParameterBinder createBinder() {

    return ParameterBinderFactory.createQueryAwareBinder(
        getQueryMethod().getParameters(), query, parser, evaluationContextProvider);
  }

  @Override
  protected Mutiny.AbstractQuery doCreateCountQuery(
      ReactiveJpaParametersParameterAccessor accessor) {
    Mutiny.Session session = accessor.getSession();
    String queryString = countQuery.get().getQueryString();

    Mutiny.AbstractQuery query =
        getQueryMethod().isNativeQuery() //
            ? session.createNativeQuery(queryString) //
            : session.createQuery(queryString, Long.class);

    QueryParameterSetter.QueryMetadata metadata = metadataCache.getMetadata(queryString, query);

    parameterBinder
        .get()
        .bind(metadata.withQuery(query), accessor, QueryParameterSetter.ErrorHandling.LENIENT);

    return query;
  }

  public DeclaredQuery getQuery() {
    return query;
  }

  public DeclaredQuery getCountQuery() {
    return countQuery.get();
  }

  protected Mutiny.AbstractQuery createReactiveJpaQuery(
      String queryString,
      ReactiveJpaQueryMethod method,
      Mutiny.Session session,
      Sort sort,
      @Nullable Pageable pageable,
      ReturnedType returnedType) {
    if (method.isModifyingQuery()) {
      return createReactiveJpaQueryForModifying(
          queryString, method, session, sort, pageable, returnedType);
    }
    if (this.query.hasConstructorExpression() || this.query.isDefaultProjection()) {
      return session.createQuery(potentiallyRewriteQuery(queryString, sort, pageable));
    }

    Class<?> typeToRead = getTypeToRead(returnedType);

    return typeToRead == null //
        ? session.createQuery(potentiallyRewriteQuery(queryString, sort, pageable)) //
        : session.createQuery(potentiallyRewriteQuery(queryString, sort, pageable), typeToRead);
  }

  private Mutiny.MutationQuery createReactiveJpaQueryForModifying(
      String queryString,
      ReactiveJpaQueryMethod method,
      Mutiny.Session session,
      Sort sort,
      @Nullable Pageable pageable,
      ReturnedType returnedType) {
    return session.createMutationQuery(potentiallyRewriteQuery(queryString, sort, pageable));
  }

  protected String potentiallyRewriteQuery(
      String originalQuery, Sort sort, @Nullable Pageable pageable) {

    return pageable != null && pageable.isPaged() //
        ? queryRewriter.rewrite(originalQuery, pageable) //
        : queryRewriter.rewrite(originalQuery, sort);
  }
}
