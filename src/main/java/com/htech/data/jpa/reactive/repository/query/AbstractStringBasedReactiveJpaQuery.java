package com.htech.data.jpa.reactive.repository.query;

import org.hibernate.reactive.stage.Stage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.QueryRewriter;
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
      Stage.SessionFactory sessionFactory,
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
  public Stage.AbstractQuery doCreateQuery(
      ReactiveJpaParametersParameterAccessor accessor,
      ReactiveJpaQueryMethod method,
      Stage.Session session) {
    String sortedQueryString =
        QueryEnhancerFactory.forQuery(query).applySorting(accessor.getSort(), query.getAlias());

    /*return Mono.zip(Mono.fromSupplier(() -> QueryEnhancerFactory.forQuery(query)
            .applySorting(accessor.getSort(), query.getAlias())),
        Mono.fromSupplier(() -> getQueryMethod().getResultProcessor().withDynamicProjection(accessor))
    ).flatMap(tuple2 -> {
      String sortedQueryString = tuple2.getT1();
      ResultProcessor processor = tuple2.getT2();

      return createReactiveJpaQuery(
          sortedQueryString,
          method,
          session,
          accessor.getSort(),
          accessor.getPageable(),
          processor.getReturnedType())
          .zipWhen(query -> Mono.fromSupplier(() -> metadataCache.getMetadata(sortedQueryString, query)));
    }).map(tuple2 -> {
      Stage.AbstractQuery query = tuple2.getT1();
      QueryParameterSetter.QueryMetadata metadata = tuple2.getT2();
      return parameterBinder.get().bindAndPrepare(query, metadata, accessor);
    });*/

    ResultProcessor processor =
        getQueryMethod().getResultProcessor().withDynamicProjection(accessor);

    Stage.AbstractQuery query =
        createReactiveJpaQuery(
            sortedQueryString,
            method,
            session,
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
  protected Stage.AbstractQuery doCreateCountQuery(
      ReactiveJpaParametersParameterAccessor accessor, Stage.Session session) {
    /*return session
    .map(
        s -> {
          String queryString = countQuery.get().getQueryString();
          Stage.SelectionQuery<?> query =
              getQueryMethod().isNativeQuery() //
                  ? s.createNativeQuery(queryString) //
                  : s.createQuery(queryString, Long.class);

          return Tuples.of(query, metadataCache.getMetadata(queryString, query));
        })
    .map(
        tuple2 -> {
          Stage.SelectionQuery<?> query = tuple2.getT1();
          QueryParameterSetter.QueryMetadata metadata = tuple2.getT2();
          parameterBinder
              .get()
              .bind(
                  metadata.withQuery(query),
                  accessor,
                  QueryParameterSetter.ErrorHandling.LENIENT);

          return query;
        });*/
    String queryString = countQuery.get().getQueryString();

    Stage.AbstractQuery query =
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

  protected Stage.AbstractQuery createReactiveJpaQuery(
      String queryString,
      ReactiveJpaQueryMethod method,
      Stage.Session session,
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

    return typeToRead == null
        ? session.createQuery(potentiallyRewriteQuery(queryString, sort, pageable)) //
        : session.createQuery(potentiallyRewriteQuery(queryString, sort, pageable), typeToRead);
    /*return session.map(s -> {
      Class<?> typeToRead = getTypeToRead(returnedType);
      return typeToRead == null
          ? s.createQuery(potentiallyRewriteQuery(queryString, sort, pageable)) //
          : s.createQuery(potentiallyRewriteQuery(queryString, sort, pageable), typeToRead);
    });*/
  }

  private Stage.AbstractQuery createReactiveJpaQueryForModifying(
      String queryString,
      ReactiveJpaQueryMethod method,
      Stage.Session session,
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
