package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Method;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.data.jpa.repository.query.*;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class ReactiveJpaQueryLookupStrategy {

  private static final Log LOG = LogFactory.getLog(ReactiveJpaQueryLookupStrategy.class);

  private static final RepositoryQuery NO_QUERY = new ReactiveJpaQueryLookupStrategy.NoQuery();

  public static QueryLookupStrategy create(
      EntityManagerFactory entityManagerFactory,
      Mutiny.SessionFactory sessionFactory,
      ReactiveJpaQueryMethodFactory queryMethodFactory,
      @Nullable QueryLookupStrategy.Key key,
      QueryMethodEvaluationContextProvider evaluationContextProvider,
      ReactiveQueryRewriterProvider queryRewriterProvider,
      EscapeCharacter escape) {

    Assert.notNull(sessionFactory, "EntityManager must not be null");
    Assert.notNull(evaluationContextProvider, "EvaluationContextProvider must not be null");

    switch (key != null ? key : QueryLookupStrategy.Key.CREATE_IF_NOT_FOUND) {
      case CREATE:
        return new CreateQueryLookupStrategy(
            entityManagerFactory,
            sessionFactory,
            queryMethodFactory,
            queryRewriterProvider,
            escape);
      case USE_DECLARED_QUERY:
        return new DeclaredQueryLookupStrategy(
            sessionFactory, queryMethodFactory, evaluationContextProvider, queryRewriterProvider);
      case CREATE_IF_NOT_FOUND:
        return new CreateIfNotFoundQueryLookupStrategy(
            sessionFactory,
            queryMethodFactory,
            new CreateQueryLookupStrategy(
                entityManagerFactory,
                sessionFactory,
                queryMethodFactory,
                queryRewriterProvider,
                escape),
            new DeclaredQueryLookupStrategy(
                sessionFactory,
                queryMethodFactory,
                evaluationContextProvider,
                queryRewriterProvider),
            queryRewriterProvider);
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported query lookup strategy %s", key));
    }
  }

  abstract static class AbstractQueryLookupStrategy implements QueryLookupStrategy {

    protected final Mutiny.SessionFactory sessionFactory;
    protected final ReactiveJpaQueryMethodFactory queryMethodFactory;
    protected final ReactiveQueryRewriterProvider queryRewriterProvider;

    public AbstractQueryLookupStrategy(
        Mutiny.SessionFactory sessionFactory,
        ReactiveJpaQueryMethodFactory queryMethodFactory,
        ReactiveQueryRewriterProvider queryRewriterProvider) {

      Assert.notNull(sessionFactory, "EntityManager must not be null");
      Assert.notNull(queryMethodFactory, "JpaQueryMethodFactory must not be null");
      this.sessionFactory = sessionFactory;
      this.queryMethodFactory = queryMethodFactory;
      this.queryRewriterProvider = queryRewriterProvider;
    }

    @Override
    public final RepositoryQuery resolveQuery(
        Method method,
        RepositoryMetadata metadata,
        ProjectionFactory factory,
        NamedQueries namedQueries) {
      ReactiveJpaQueryMethod queryMethod = queryMethodFactory.build0(method, metadata, factory);
      return resolveQuery(
          queryMethod,
          queryRewriterProvider.getQueryRewriter(queryMethod),
          sessionFactory,
          namedQueries);
    }

    protected abstract RepositoryQuery resolveQuery(
        ReactiveJpaQueryMethod method,
        QueryRewriter queryRewriter,
        Mutiny.SessionFactory sessionFactory,
        NamedQueries namedQueries);
  }

  static class CreateQueryLookupStrategy extends AbstractQueryLookupStrategy {

    protected final EntityManagerFactory entityManagerFactory;
    protected final EscapeCharacter escape;

    public CreateQueryLookupStrategy(
        EntityManagerFactory entityManagerFactory,
        Mutiny.SessionFactory sessionFactory,
        ReactiveJpaQueryMethodFactory queryMethodFactory,
        ReactiveQueryRewriterProvider queryRewriterProvider,
        EscapeCharacter escape) {

      super(sessionFactory, queryMethodFactory, queryRewriterProvider);
      this.entityManagerFactory = entityManagerFactory;
      this.escape = escape;
    }

    @Override
    protected RepositoryQuery resolveQuery(
        ReactiveJpaQueryMethod method,
        QueryRewriter queryRewriter,
        Mutiny.SessionFactory sessionFactory,
        NamedQueries namedQueries) {
      return new PartTreeReactiveJpaQuery(method, entityManagerFactory, sessionFactory, escape);
    }
  }

  static class DeclaredQueryLookupStrategy extends AbstractQueryLookupStrategy {

    private final QueryMethodEvaluationContextProvider evaluationContextProvider;

    public DeclaredQueryLookupStrategy(
        Mutiny.SessionFactory sessionFactory,
        ReactiveJpaQueryMethodFactory queryMethodFactory,
        QueryMethodEvaluationContextProvider evaluationContextProvider,
        ReactiveQueryRewriterProvider queryRewriterProvider) {

      super(sessionFactory, queryMethodFactory, queryRewriterProvider);

      this.evaluationContextProvider = evaluationContextProvider;
    }

    @Override
    protected RepositoryQuery resolveQuery(
        ReactiveJpaQueryMethod method,
        QueryRewriter queryRewriter,
        Mutiny.SessionFactory sessionFactory,
        NamedQueries namedQueries) {
      // TODO: important
      if (method.isProcedureQuery()) {
        //        return ReactiveJpaQueryFactory.INSTANCE.fromProcedureAnnotation(method,
        // sessionFactory);
      }

      if (StringUtils.hasText(method.getAnnotatedQuery())) {

        if (method.hasAnnotatedQueryName()) {
          LOG.warn(
              String.format(
                  "Query method %s is annotated with both, a query and a query name; Using the declared query",
                  method));
        }

        return ReactiveJpaQueryFactory.INSTANCE.fromMethodWithQueryString(
            method,
            sessionFactory,
            method.getRequiredAnnotatedQuery(),
            getCountQuery(method, namedQueries, sessionFactory),
            queryRewriter,
            evaluationContextProvider);
      }

      String name = method.getNamedQueryName();
      if (namedQueries.hasQuery(name)) {
        return ReactiveJpaQueryFactory.INSTANCE.fromMethodWithQueryString(
            method,
            sessionFactory,
            namedQueries.getQuery(name),
            getCountQuery(method, namedQueries, sessionFactory),
            queryRewriter,
            evaluationContextProvider);
      }

      RepositoryQuery query = NamedQuery.lookupFrom(method, sessionFactory);

      return query != null //
          ? query //
          : NO_QUERY;
    }

    @Nullable
    private String getCountQuery(
        ReactiveJpaQueryMethod method,
        NamedQueries namedQueries,
        Mutiny.SessionFactory sessionFactory) {
      // TODO

      return null;
    }
  }

  private static class CreateIfNotFoundQueryLookupStrategy extends AbstractQueryLookupStrategy {

    private final DeclaredQueryLookupStrategy lookupStrategy;
    private final CreateQueryLookupStrategy createStrategy;

    public CreateIfNotFoundQueryLookupStrategy(
        Mutiny.SessionFactory sessionFactory,
        ReactiveJpaQueryMethodFactory queryMethodFactory,
        CreateQueryLookupStrategy createStrategy,
        DeclaredQueryLookupStrategy lookupStrategy,
        ReactiveQueryRewriterProvider queryRewriterProvider) {

      super(sessionFactory, queryMethodFactory, queryRewriterProvider);

      Assert.notNull(createStrategy, "CreateQueryLookupStrategy must not be null");
      Assert.notNull(lookupStrategy, "DeclaredQueryLookupStrategy must not be null");

      this.createStrategy = createStrategy;
      this.lookupStrategy = lookupStrategy;
    }

    @Override
    protected RepositoryQuery resolveQuery(
        ReactiveJpaQueryMethod method,
        QueryRewriter queryRewriter,
        Mutiny.SessionFactory sessionFactory,
        NamedQueries namedQueries) {

      RepositoryQuery lookupQuery =
          lookupStrategy.resolveQuery(method, queryRewriter, sessionFactory, namedQueries);

      if (lookupQuery != NO_QUERY) {
        return lookupQuery;
      }

      return createStrategy.resolveQuery(method, queryRewriter, sessionFactory, namedQueries);
    }
  }

  static class NoQuery implements RepositoryQuery {

    @Override
    public Object execute(Object[] parameters) {
      throw new IllegalStateException("NoQuery should not be executed!");
    }

    @Override
    public QueryMethod getQueryMethod() {
      throw new IllegalStateException("NoQuery does not have a QueryMethod!");
    }
  }
}
