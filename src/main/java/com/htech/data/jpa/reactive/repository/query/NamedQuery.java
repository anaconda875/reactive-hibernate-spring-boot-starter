package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Tuple;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.reactive.stage.Stage;
import org.springframework.data.repository.query.*;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;

public class NamedQuery extends AbstractReactiveJpaQuery {

  private static final String CANNOT_EXTRACT_QUERY =
      "Your persistence provider does not support extracting the JPQL query from a "
          + "named query thus you can't use Pageable inside your query method; Make sure you "
          + "have a JpaDialect configured at your EntityManagerFactoryBean as this affects "
          + "discovering the concrete persistence provider";

  private static final Log LOG = LogFactory.getLog(NamedQuery.class);

  private final EntityManagerFactory emf;
  private final String queryName;
  private final String countQueryName;
  private final @Nullable String countProjection;
  private final QueryParameterSetter.QueryMetadataCache metadataCache;

  private final Lock lock = new ReentrantLock();

  private boolean namedCountQueryIsPresent;
  private DeclaredQuery declaredQuery;

  private boolean fullyInitialized;

  private NamedQuery(
      ReactiveJpaQueryMethod method,
      Stage.SessionFactory sessionFactory,
      EntityManagerFactory emf) {
    super(method, sessionFactory);

    this.emf = emf;
    this.queryName = method.getNamedQueryName();
    this.countQueryName = method.getNamedCountQueryName();
    this.countProjection = method.getCountQueryProjection();
    this.metadataCache = new QueryParameterSetter.QueryMetadataCache();

    Parameters<?, ?> parameters = method.getParameters();
    if (parameters.hasSortParameter()) {
      throw new IllegalStateException(
          String.format(
              "Finder method %s is backed by a NamedQuery and must "
                  + "not contain a sort parameter as we cannot modify the query; Use @Query instead",
              method));
    }

    if (parameters.hasPageableParameter()) {
      LOG.warn(
          String.format(
              "Finder method %s is backed by a NamedQuery but contains a Pageable parameter; Sorting delivered via this Pageable will not be applied",
              method));
    }

    this.namedCountQueryIsPresent = hasNamedQuery(emf, countQueryName);
  }

  // TODO
  /* static boolean hasNamedQuery(Stage.SessionFactory em, String queryName) {
    try {
      em.createNamedQuery(queryName);
      return true;
    } catch (IllegalArgumentException e) {

      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Did not find named query %s", queryName));
      }
      return false;
    } finally {
    }
  }*/

  @Nullable
  public static RepositoryQuery lookupFrom(
      ReactiveJpaQueryMethod method,
      Stage.SessionFactory sessionFactory,
      EntityManagerFactory emf) {
    String queryName = method.getNamedQueryName();

    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("Looking up named query %s", queryName));
    }

    // TODO
    if (!hasNamedQuery(emf, queryName)) {
      return null;
    }

    if (method.isScrollQuery()) {
      throw QueryCreationException.create(
          method, "Scroll queries are not supported using String-based queries");
    }

    try {

      RepositoryQuery query = new NamedQuery(method, sessionFactory, emf);
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Found named query %s", queryName));
      }
      return query;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static boolean hasNamedQuery(EntityManagerFactory emf, String queryName) {
    try (EntityManager lookupEm = emf.createEntityManager()) {
      lookupEm.createNamedQuery(queryName);
      return true;
    } catch (IllegalArgumentException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Did not find named query %s", queryName));
      }
      return false;
    }
  }

  @Override
  protected Mono<Stage.AbstractQuery> doCreateQuery(
      Mono<Stage.Session> session,
      ReactiveJpaParametersParameterAccessor accessor,
      ReactiveJpaQueryMethod method) {
    ReactiveJpaQueryMethod queryMethod = getQueryMethod();
    ResultProcessor processor = queryMethod.getResultProcessor().withDynamicProjection(accessor);
    //    Class<?> typeToRead = getTypeToRead(processor.getReturnedType());

    return session
        .doOnNext(this::initialize)
        .zipWhen(__ -> Mono.fromSupplier(() -> getTypeToRead(processor.getReturnedType())))
        .map(
            t -> {
              Stage.Session s = t.getT1();
              Optional<Class<?>> typeToRead = t.getT2();

              return typeToRead.isEmpty()
                  ? s.createNamedQuery(queryName)
                  : s.createNamedQuery(queryName, typeToRead.get());
            })
        .zipWhen(q -> Mono.fromSupplier(() -> metadataCache.getMetadata(queryName, q)))
        .flatMap(t -> parameterBinder.get().bindAndPrepare(t.getT1(), t.getT2(), accessor));
    //    Stage.AbstractQuery query = typeToRead == null //
    //        ? em.createNamedQuery(queryName) //
    //        : em.createNamedQuery(queryName, typeToRead);

    //    QueryParameterSetter.QueryMetadata metadata = metadataCache.getMetadata(queryName, query);

    //    return parameterBinder.get().bindAndPrepare(query, metadata, accessor);
    // TODO
    //    return Mono.empty();
  }

  @Override
  protected Mono<Stage.AbstractQuery> doCreateCountQuery(
      Mono<Stage.Session> session, ReactiveJpaParametersParameterAccessor accessor) {
    //      Mono<Stage.AbstractQuery> countQuery;
    //      String cacheKey = "";
    return session
        .doOnNext(this::initialize)
        .flatMap(
            s -> {
              String cacheKey;
              Stage.AbstractQuery countQuery;
              if (namedCountQueryIsPresent) {
                cacheKey = countQueryName;
                countQuery = s.createNamedQuery(countQueryName, Long.class);
              } else {
                String countQueryString =
                    declaredQuery.deriveCountQuery(null, countProjection).getQueryString();
                cacheKey = countQueryString;
                countQuery = s.createQuery(countQueryString, Long.class);
              }

              QueryParameterSetter.QueryMetadata metadata =
                  metadataCache.getMetadata(cacheKey, countQuery);

              return parameterBinder.get().bind(countQuery, metadata, accessor);
            });

    //    if (namedCountQueryIsPresent) {
    //      cacheKey = countQueryName;
    //      countQuery = session.map(s -> s.createNamedQuery(countQueryName, Long.class));
    //    } else {
    //      String countQueryString = declaredQuery.deriveCountQuery(null,
    // countProjection).getQueryString();
    //      cacheKey = countQueryString;
    //      countQuery = em.createQuery(countQueryString, Long.class);
    //    }

    //    QueryParameterSetter.QueryMetadata metadata = metadataCache.getMetadata(cacheKey,
    // countQuery);
    //
    //    return parameterBinder.get().bind(countQuery, metadata, accessor);
    //    // TODO
    //    return null;
  }

  @Override
  protected Optional<Class<?>> getTypeToRead(ReturnedType returnedType) {
    if (getQueryMethod().isNativeQuery()) {
      Class<?> type = returnedType.getReturnedType();
      Class<?> domainType = returnedType.getDomainType();

      // Domain or subtype -> use return type
      if (domainType.isAssignableFrom(type)) {
        return Optional.of(type);
      }

      // Domain type supertype -> use domain type
      if (type.isAssignableFrom(domainType)) {
        return Optional.of(domainType);
      }

      // Tuples for projection interfaces or explicit SQL mappings for everything else
      return type.isInterface() ? Optional.of(Tuple.class) : Optional.empty();
    }

    return declaredQuery.hasConstructorExpression() //
        ? Optional.empty() //
        : super.getTypeToRead(returnedType);
  }

  private void initialize(Stage.Session session) {
    if (fullyInitialized) {
      return;
    }

    try {
      lock.lock();
      ReactiveJpaQueryExtractor extractor = method.getQueryExtractor();
      //
      //      Parameters<?, ?> parameters = method.getParameters();
      //      if (parameters.hasSortParameter()) {
      //        throw new IllegalStateException(
      //            String.format(
      //                "Finder method %s is backed by a NamedQuery and must "
      //                    + "not contain a sort parameter as we cannot modify the query; Use
      // @Query instead",
      //                method));
      //      }
      //
      //      this.namedCountQueryIsPresent = hasNamedQuery(emf, countQueryName);

      Stage.Query query = session.createNamedQuery(queryName);
      String queryString = extractor.extractQueryString(query);
      this.declaredQuery = DeclaredQuery.of(queryString, false);

      boolean needToCreateCountQuery =
          !namedCountQueryIsPresent && method.getParameters().hasLimitingParameters();

      if (needToCreateCountQuery && !extractor.canExtractQuery()) {
        throw QueryCreationException.create(method, CANNOT_EXTRACT_QUERY);
      }

      fullyInitialized = true;
    } finally {
      lock.unlock();
    }
  }
}
