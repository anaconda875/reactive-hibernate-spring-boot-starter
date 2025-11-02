package com.htech.data.jpa.reactive.repository.query;

import java.util.Objects;
import java.util.Optional;
import org.hibernate.reactive.stage.Stage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.expression.ReactiveValueEvaluationContextProvider;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.data.util.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentLruCache;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

/**
 * @author Bao.Ngo
 */
public class AbstractStringBasedReactiveJpaQuery extends AbstractReactiveJpaQuery {

  protected final DeclaredQuery query;
  protected final Lazy<DeclaredQuery> countQuery;
  protected final ValueExpressionDelegate delegate;
  protected final QueryParameterSetter.QueryMetadataCache metadataCache =
      new QueryParameterSetter.QueryMetadataCache();
  protected final QueryRewriter queryRewriter;
  protected final ReactiveValueEvaluationContextProvider valueExpressionContextProvider;
  protected final QuerySortRewriter querySortRewriter;

  public AbstractStringBasedReactiveJpaQuery(
      ReactiveJpaQueryMethod method,
      Stage.SessionFactory sessionFactory,
      String queryString,
      @Nullable String countQueryString,
      QueryRewriter queryRewriter,
      ValueExpressionDelegate delegate) {
    super(method, sessionFactory);

    Assert.hasText(queryString, "Query string must not be null or empty");
    Assert.notNull(
        delegate, "ValueExpressionDelegate must not be null");
    Assert.notNull(queryRewriter, "QueryRewriter must not be null");

    this.delegate = delegate;
    ReactiveJpaParameters parameters = method.getParameters();
    this.valueExpressionContextProvider = (ReactiveValueEvaluationContextProvider) delegate.createValueContextProvider(parameters);
    this.query =
        new ExpressionBasedStringQuery(
            queryString, method.getEntityInformation(), delegate, method.isNativeQuery());

    this.countQuery =
        Lazy.of(
            () -> {
              DeclaredQuery countQuery =
                  query.deriveCountQuery(countQueryString, method.getCountQueryProjection());
              return ExpressionBasedStringQuery.from(
                  countQuery, method.getEntityInformation(), delegate, method.isNativeQuery());
            });

    this.queryRewriter = queryRewriter;
    if (parameters.hasPageableParameter() || parameters.hasSortParameter()) {
      this.querySortRewriter = new CachingQuerySortRewriter();
    } else {
      this.querySortRewriter = NoOpQuerySortRewriter.INSTANCE;
    }

    Assert.isTrue(
        method.isNativeQuery() || !query.usesJdbcStyleParameters(),
        "JDBC style parameters (?) are not supported for JPA queries");
  }

  @Override
  protected Mono<Stage.AbstractQuery> doCreateQuery(
      Mono<Stage.Session> session,
      ReactiveJpaParametersParameterAccessor accessor,
      ReactiveJpaQueryMethod method) {
    return Mono.zip(
            Mono.fromSupplier(
                () ->
                    querySortRewriter.getSorted(query, accessor.getSort())
                    /*QueryEnhancerFactory.forQuery(query)
                        .applySorting(accessor.getSort(), query.getAlias())*/),
            Mono.fromSupplier(
                () -> getQueryMethod().getResultProcessor().withDynamicProjection(accessor)))
        .flatMap(
            tuple2 -> {
              String sortedQueryString = tuple2.getT1();
              ResultProcessor processor = tuple2.getT2();

              return createReactiveJpaQuery(
                      session,
                      sortedQueryString,
                      method,
                      accessor.getSort(),
                      accessor.getPageable(),
                      processor.getReturnedType())
                  .zipWhen(
                      query ->
                          Mono.fromSupplier(
                              () -> metadataCache.getMetadata(sortedQueryString, query)));
            })
        .flatMap(
            tuple2 -> {
              Stage.AbstractQuery query = tuple2.getT1();
              QueryParameterSetter.QueryMetadata metadata = tuple2.getT2();
              return parameterBinder.get().bindAndPrepare(query, metadata, accessor);
            });
  }

  //  private Mono<R2dbcSpELExpressionEvaluator>
  // getSpelEvaluator(ReactiveJpaParametersParameterAccessor accessor) {
  //
  //    return evaluationContextProvider
  //        .getEvaluationContextLater(getQueryMethod().getParameters(), accessor.getValues(),
  // expressionDependencies)
  //        .<R2dbcSpELExpressionEvaluator> map(
  //            context -> new DefaultR2dbcSpELExpressionEvaluator(expressionParser, context))
  //        .defaultIfEmpty(DefaultR2dbcSpELExpressionEvaluator.unsupported());
  //  }

  @Override
  protected ParameterBinder createBinder() {
    return ParameterBinderFactory.createQueryAwareBinder(
        getQueryMethod().getParameters(), query, delegate, valueExpressionContextProvider);
  }

  @Override
  protected Mono<Stage.AbstractQuery> doCreateCountQuery(
      Mono<Stage.Session> session, ReactiveJpaParametersParameterAccessor accessor) {
    return session
        .map(
            s -> {
              String queryString = countQuery.get().getQueryString();
              Stage.SelectionQuery<?> query =
                  getQueryMethod().isNativeQuery()
                      ? s.createNativeQuery(queryString)
                      : s.createQuery(queryString, Long.class);

              return Tuples.of(query, metadataCache.getMetadata(queryString, query));
            })
        .flatMap(
            tuple2 -> {
              Stage.SelectionQuery<?> query = tuple2.getT1();
              QueryParameterSetter.QueryMetadata metadata = tuple2.getT2();
              return parameterBinder
                  .get()
                  .bind(
                      metadata.withQuery(query),
                      accessor,
                      QueryParameterSetter.ErrorHandling.LENIENT)
                  .thenReturn(query);
            });
  }

  public DeclaredQuery getQuery() {
    return query;
  }

  public DeclaredQuery getCountQuery() {
    return countQuery.get();
  }

  protected Mono<Stage.AbstractQuery> createReactiveJpaQuery(
      Mono<Stage.Session> session,
      String queryString,
      ReactiveJpaQueryMethod method,
      Sort sort,
      @Nullable Pageable pageable,
      ReturnedType returnedType) {
    return session.map(
        s -> {
          if (method.isModifyingQuery()) {
            return createReactiveJpaQueryForModifying(
                queryString, method, s, sort, pageable, returnedType);
          }

          if (this.query.hasConstructorExpression() || this.query.isDefaultProjection()) {
            return s.createQuery(potentiallyRewriteQuery(queryString, sort, pageable));
          }

          Optional<Class<?>> typeToRead = getTypeToRead(returnedType);
          return typeToRead.isEmpty()
              ? s.createQuery(potentiallyRewriteQuery(queryString, sort, pageable))
              : s.createQuery(
                  potentiallyRewriteQuery(queryString, sort, pageable), typeToRead.get());
        });
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

  String applySorting(CacheableQuery cacheableQuery) {
    return QueryEnhancerFactory.forQuery(cacheableQuery.getDeclaredQuery()).applySorting(cacheableQuery.getSort(),
        cacheableQuery.getAlias());
  }

  protected String potentiallyRewriteQuery(
      String originalQuery, Sort sort, @Nullable Pageable pageable) {
    return pageable != null && pageable.isPaged()
        ? queryRewriter.rewrite(originalQuery, pageable)
        : queryRewriter.rewrite(originalQuery, sort);
  }

  static class CacheableQuery {

    private final DeclaredQuery declaredQuery;
    private final String queryString;
    private final Sort sort;

    CacheableQuery(DeclaredQuery query, Sort sort) {

      this.declaredQuery = query;
      this.queryString = query.getQueryString();
      this.sort = sort;
    }

    DeclaredQuery getDeclaredQuery() {
      return declaredQuery;
    }

    Sort getSort() {
      return sort;
    }

    @Nullable
    String getAlias() {
      return declaredQuery.getAlias();
    }

    @Override
    public boolean equals(Object o) {

      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      CacheableQuery that = (CacheableQuery) o;

      if (!Objects.equals(queryString, that.queryString)) {
        return false;
      }
      return Objects.equals(sort, that.sort);
    }

    @Override
    public int hashCode() {

      int result = queryString != null ? queryString.hashCode() : 0;
      result = 31 * result + (sort != null ? sort.hashCode() : 0);
      return result;
    }
  }

  protected interface QuerySortRewriter {
    String getSorted(DeclaredQuery query, Sort sort);
  }

  enum NoOpQuerySortRewriter implements QuerySortRewriter {
    INSTANCE;

    public String getSorted(DeclaredQuery query, Sort sort) {

      if (sort.isSorted()) {
        throw new UnsupportedOperationException("NoOpQueryCache does not support sorting");
      }

      return query.getQueryString();
    }
  }

  class CachingQuerySortRewriter implements QuerySortRewriter {

    private final ConcurrentLruCache<CacheableQuery, String> queryCache = new ConcurrentLruCache<>(16,
        AbstractStringBasedReactiveJpaQuery.this::applySorting);

    @Override
    public String getSorted(DeclaredQuery query, Sort sort) {

      if (sort.isUnsorted()) {
        return query.getQueryString();
      }

      return queryCache.get(new CacheableQuery(query, sort));
    }
  }
}
