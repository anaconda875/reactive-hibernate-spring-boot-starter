package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Method;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.reactive.stage.Stage;
import org.springframework.data.jpa.provider.PersistenceProvider;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.data.jpa.repository.query.*;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.*;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Bao.Ngo
 */
public class ReactiveJpaQueryLookupStrategy {

  private static final Log LOG = LogFactory.getLog(ReactiveJpaQueryLookupStrategy.class);

  private static final RepositoryQuery NO_QUERY = new ReactiveJpaQueryLookupStrategy.NoQuery();

  public static QueryLookupStrategy create(
      EntityManagerFactory emf,
      Stage.SessionFactory sessionFactory,
      ReactiveJpaQueryMethodFactory queryMethodFactory,
      @Nullable QueryLookupStrategy.Key key,
      ValueExpressionDelegate delegate,
      ReactiveQueryRewriterProvider queryRewriterProvider,
      EscapeCharacter escape) {

    Assert.notNull(emf, "EntityManager must not be null");
    Assert.notNull(delegate, "ValueExpressionDelegate must not be null");

    return switch (key != null ? key : QueryLookupStrategy.Key.CREATE_IF_NOT_FOUND) {
      case CREATE -> new CreateQueryLookupStrategy(
          emf, sessionFactory, queryMethodFactory, delegate, queryRewriterProvider, escape);
      case USE_DECLARED_QUERY -> new DeclaredQueryLookupStrategy(
          emf, sessionFactory, queryMethodFactory, delegate, queryRewriterProvider);
      case CREATE_IF_NOT_FOUND -> new CreateIfNotFoundQueryLookupStrategy(
          sessionFactory,
          queryMethodFactory,
          new CreateQueryLookupStrategy(
              emf, sessionFactory, queryMethodFactory, delegate, queryRewriterProvider, escape),
          new DeclaredQueryLookupStrategy(
              emf, sessionFactory, queryMethodFactory, delegate, queryRewriterProvider),
          queryRewriterProvider);
    };
  }

  abstract static class AbstractQueryLookupStrategy implements QueryLookupStrategy {

    protected final Stage.SessionFactory sessionFactory;
    protected final ReactiveJpaQueryMethodFactory queryMethodFactory;
    protected final ReactiveQueryRewriterProvider queryRewriterProvider;

    public AbstractQueryLookupStrategy(
        Stage.SessionFactory sessionFactory,
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
        Stage.SessionFactory sessionFactory,
        NamedQueries namedQueries);
  }

  static class CreateQueryLookupStrategy extends AbstractQueryLookupStrategy {

    protected final EntityManagerFactory entityManagerFactory;
    private final ValueExpressionDelegate delegate;
    protected final EscapeCharacter escape;

    public CreateQueryLookupStrategy(
        EntityManagerFactory entityManagerFactory,
        Stage.SessionFactory sessionFactory,
        ReactiveJpaQueryMethodFactory queryMethodFactory,
        ValueExpressionDelegate delegate,
        ReactiveQueryRewriterProvider queryRewriterProvider,
        EscapeCharacter escape) {

      super(sessionFactory, queryMethodFactory, queryRewriterProvider);
      this.entityManagerFactory = entityManagerFactory;
      this.delegate = delegate;
      this.escape = escape;
    }

    @Override
    protected RepositoryQuery resolveQuery(
        ReactiveJpaQueryMethod method,
        QueryRewriter queryRewriter,
        Stage.SessionFactory sessionFactory,
        NamedQueries namedQueries) {
      return new PartTreeReactiveJpaQuery(method, entityManagerFactory, sessionFactory, escape);
    }
  }

  static class DeclaredQueryLookupStrategy extends AbstractQueryLookupStrategy {

    private final EntityManagerFactory entityManagerFactory;
    private final ValueExpressionDelegate delegate;

    public DeclaredQueryLookupStrategy(
        EntityManagerFactory entityManagerFactory,
        Stage.SessionFactory sessionFactory,
        ReactiveJpaQueryMethodFactory queryMethodFactory,
        ValueExpressionDelegate delegate,
        ReactiveQueryRewriterProvider queryRewriterProvider) {

      super(sessionFactory, queryMethodFactory, queryRewriterProvider);
      this.entityManagerFactory = entityManagerFactory;
      this.delegate = delegate;
    }

    @Override
    protected RepositoryQuery resolveQuery(
        ReactiveJpaQueryMethod method,
        QueryRewriter queryRewriter,
        Stage.SessionFactory sessionFactory,
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
            getCountQuery(method, namedQueries, entityManagerFactory, sessionFactory),
            queryRewriter,
            delegate);
      }

      String name = method.getNamedQueryName();
      if (namedQueries.hasQuery(name)) {
        return ReactiveJpaQueryFactory.INSTANCE.fromMethodWithQueryString(
            method,
            sessionFactory,
            namedQueries.getQuery(name),
            getCountQuery(method, namedQueries, entityManagerFactory, sessionFactory),
            queryRewriter,
            delegate);
      }

      RepositoryQuery query = NamedQuery.lookupFrom(method, sessionFactory, entityManagerFactory);

      return query != null ? query : NO_QUERY;
    }

    @Nullable
    private String getCountQuery(
        ReactiveJpaQueryMethod method,
        NamedQueries namedQueries,
        EntityManagerFactory emf,
        Stage.SessionFactory sessionFactory) {
      if (StringUtils.hasText(method.getCountQuery())) {
        return method.getCountQuery();
      }

      String queryName = method.getNamedCountQueryName();

      if (!StringUtils.hasText(queryName)) {
        return method.getCountQuery();
      }

      if (namedQueries.hasQuery(queryName)) {
        return namedQueries.getQuery(queryName);
      }

      boolean namedQuery = NamedQuery.hasNamedQuery(emf, queryName);

      if (namedQuery) {
        try (EntityManager em = emf.createEntityManager()) {
          return PersistenceProvider.fromEntityManager(em)
              .extractQueryString(em.createNamedQuery(queryName));
        } catch (Exception e) {
          LOG.warn(e.getMessage(), e);
        }
      }

      return null;
    }
  }

  private static class CreateIfNotFoundQueryLookupStrategy extends AbstractQueryLookupStrategy {

    private final DeclaredQueryLookupStrategy lookupStrategy;
    private final CreateQueryLookupStrategy createStrategy;

    public CreateIfNotFoundQueryLookupStrategy(
        Stage.SessionFactory sessionFactory,
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
        Stage.SessionFactory sessionFactory,
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
