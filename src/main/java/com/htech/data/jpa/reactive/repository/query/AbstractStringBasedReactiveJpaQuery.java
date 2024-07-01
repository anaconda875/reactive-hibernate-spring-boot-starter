package com.htech.data.jpa.reactive.repository.query;

import java.util.Optional;
import org.hibernate.reactive.stage.Stage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.data.repository.query.ReactiveQueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.util.Lazy;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

/**
 * @author Bao.Ngo
 */
public class AbstractStringBasedReactiveJpaQuery extends AbstractReactiveJpaQuery {

  protected final DeclaredQuery query;
  protected final Lazy<DeclaredQuery> countQuery;
  protected final ReactiveQueryMethodEvaluationContextProvider evaluationContextProvider;
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
      ReactiveQueryMethodEvaluationContextProvider evaluationContextProvider,
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
  protected Mono<Stage.AbstractQuery> doCreateQuery(
      Mono<Stage.Session> session,
      ReactiveJpaParametersParameterAccessor accessor,
      ReactiveJpaQueryMethod method) {
    //    String sortedQueryString =
    //        QueryEnhancerFactory.forQuery(query).applySorting(accessor.getSort(),
    // query.getAlias());

    return Mono.zip(
            Mono.fromSupplier(
                () ->
                    QueryEnhancerFactory.forQuery(query)
                        .applySorting(accessor.getSort(), query.getAlias())),
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

    //    ResultProcessor processor =
    //        getQueryMethod().getResultProcessor().withDynamicProjection(accessor);
    //
    //    Stage.AbstractQuery query =
    //        createReactiveJpaQuery(
    //            session, sortedQueryString,
    //            method,
    //            accessor.getSort(),
    //            accessor.getPageable(),
    //            processor.getReturnedType());
    //
    //    QueryParameterSetter.QueryMetadata metadata =
    //        metadataCache.getMetadata(sortedQueryString, query);

    // it is ok to reuse the binding contained in the ParameterBinder although we create a new query
    // String because the
    // parameters in the query do not change.
    //    return parameterBinder.get().bindAndPrepare(query, metadata, accessor);
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
        getQueryMethod().getParameters(), query, parser, evaluationContextProvider);
  }

  @Override
  protected Mono<Stage.AbstractQuery> doCreateCountQuery(
      Mono<Stage.Session> session, ReactiveJpaParametersParameterAccessor accessor) {
    return session
        .map(
            s -> {
              String queryString = countQuery.get().getQueryString();
              Stage.SelectionQuery<?> query =
                  getQueryMethod().isNativeQuery() //
                      ? s.createNativeQuery(queryString) //
                      : s.createQuery(queryString, Long.class);

              return Tuples.of(query, metadataCache.getMetadata(queryString, query));
            })
        .flatMap(
            tuple2 -> {
              Stage.SelectionQuery<?> query = tuple2.getT1();
              QueryParameterSetter.QueryMetadata metadata = tuple2.getT2();
              return parameterBinder
                  .get()
                  //              .doOnNext(pb -> pb.bind(metadata.withQuery(query), accessor,
                  // QueryParameterSetter.ErrorHandling.LENIENT))
                  //              .thenReturn(query);
                  .bind(
                      metadata.withQuery(query),
                      accessor,
                      QueryParameterSetter.ErrorHandling.LENIENT)
                  .thenReturn(query);

              //          return query;
            });
    //    String queryString = countQuery.get().getQueryString();
    //
    //    Stage.AbstractQuery query =
    //        getQueryMethod().isNativeQuery() //
    //            ? session.createNativeQuery(queryString) //
    //            : session.createQuery(queryString, Long.class);
    //
    //    QueryParameterSetter.QueryMetadata metadata = metadataCache.getMetadata(queryString,
    // query);
    //
    //    parameterBinder
    //        .get()
    //        .bind(metadata.withQuery(query), accessor,
    // QueryParameterSetter.ErrorHandling.LENIENT);
    //
    //    return query;
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
    //    if (method.isModifyingQuery()) {
    //      return createReactiveJpaQueryForModifying(
    //          queryString, method, session, sort, pageable, returnedType);
    //    }
    //    if (this.query.hasConstructorExpression() || this.query.isDefaultProjection()) {
    //      return session.createQuery(potentiallyRewriteQuery(queryString, sort, pageable));
    //    }
    //
    //    Class<?> typeToRead = getTypeToRead(returnedType);
    //
    //    return typeToRead == null
    //        ? session.createQuery(potentiallyRewriteQuery(queryString, sort, pageable)) //
    //        : session.createQuery(potentiallyRewriteQuery(queryString, sort, pageable),
    // typeToRead);
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
              ? s.createQuery(potentiallyRewriteQuery(queryString, sort, pageable)) //
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

  protected String potentiallyRewriteQuery(
      String originalQuery, Sort sort, @Nullable Pageable pageable) {

    return pageable != null && pageable.isPaged() //
        ? queryRewriter.rewrite(originalQuery, pageable) //
        : queryRewriter.rewrite(originalQuery, sort);
  }
}
