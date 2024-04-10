package com.htech.jpa.reactive.repository.query;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.data.repository.query.*;
import org.springframework.lang.Nullable;

public class NamedQuery extends AbstractReactiveJpaQuery {

  private static final String CANNOT_EXTRACT_QUERY = "Your persistence provider does not support extracting the JPQL query from a "
      + "named query thus you can't use Pageable inside your query method; Make sure you "
      + "have a JpaDialect configured at your EntityManagerFactoryBean as this affects "
      + "discovering the concrete persistence provider";

  private static final Log LOG = LogFactory.getLog(NamedQuery.class);

  private final String queryName;
//  private final String countQueryName;
//  private final @Nullable String countProjection;
//  private final boolean namedCountQueryIsPresent;
//  private final DeclaredQuery declaredQuery;
  private final QueryParameterSetter.QueryMetadataCache metadataCache;

  /**
   * Creates a new {@link NamedQuery}.
   */
  private NamedQuery(ReactiveJpaQueryMethod method, Mutiny.SessionFactory sessionFactory) {

    super(method, sessionFactory);

    this.queryName = method.getNamedQueryName();
//    this.countQueryName = method.getNamedCountQueryName();
//    QueryExtractor extractor = method.getQueryExtractor();
//    this.countProjection = method.getCountQueryProjection();

    Parameters<?, ?> parameters = method.getParameters();

    if (parameters.hasSortParameter()) {
      throw new IllegalStateException(String.format("Finder method %s is backed by a NamedQuery and must "
          + "not contain a sort parameter as we cannot modify the query; Use @Query instead", method));
    }

//    this.namedCountQueryIsPresent = hasNamedQuery(sessionFactory, countQueryName);

//    Query query = sessionFactory.createNamedQuery(queryName);
//    String queryString = extractor.extractQueryString(query);
//
//    this.declaredQuery = DeclaredQuery.of(queryString, false);

//    boolean weNeedToCreateCountQuery = !namedCountQueryIsPresent && method.getParameters().hasLimitingParameters();
//    boolean cantExtractQuery = !extractor.canExtractQuery();

//    if (weNeedToCreateCountQuery && cantExtractQuery) {
//      throw QueryCreationException.create(method, CANNOT_EXTRACT_QUERY);
//    }

    if (parameters.hasPageableParameter()) {
      LOG.warn(String.format(
          "Finder method %s is backed by a NamedQuery but contains a Pageable parameter; Sorting delivered via this Pageable will not be applied",
          method));
    }

    this.metadataCache = new QueryParameterSetter.QueryMetadataCache();
  }

  //TODO
 /* static boolean hasNamedQuery(Mutiny.SessionFactory em, String queryName) {



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

  /**
   * Looks up a named query for the given {@link org.springframework.data.repository.query.QueryMethod}.
   *
   * @param method must not be {@literal null}.
   * @param sessionFactory     must not be {@literal null}.
   */
  @Nullable
  public static RepositoryQuery lookupFrom(ReactiveJpaQueryMethod method, Mutiny.SessionFactory sessionFactory) {

    final String queryName = method.getNamedQueryName();

    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("Looking up named query %s", queryName));
    }

    //TODO
    if (!hasNamedQuery(sessionFactory, queryName)) {
      return null;
    }

    if (method.isScrollQuery()) {
      throw QueryCreationException.create(method, "Scroll queries are not supported using String-based queries");
    }

    try {

      RepositoryQuery query = new NamedQuery(method, sessionFactory);
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Found named query %s", queryName));
      }
      return query;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static boolean hasNamedQuery(Mutiny.SessionFactory sessionFactory, String queryName) {
    //TODO: important
    return false;
  }

  @Override
  protected Mutiny.AbstractQuery doCreateQuery(ReactiveJpaParametersParameterAccessor accessor, ReactiveJpaQueryMethod method) {
    ReactiveJpaQueryMethod queryMethod = getQueryMethod();
    ResultProcessor processor = queryMethod.getResultProcessor().withDynamicProjection(accessor);

    Class<?> typeToRead = getTypeToRead(processor.getReturnedType());

    Mutiny.AbstractQuery query = /*typeToRead == null //
        ? em.createNamedQuery(queryName) //
        : em.createNamedQuery(queryName, typeToRead);*/ null;

    QueryParameterSetter.QueryMetadata metadata = metadataCache.getMetadata(queryName, query);

    return parameterBinder.get().bindAndPrepare(query, metadata, accessor);
  }

  @Override
  protected Mutiny.AbstractQuery doCreateCountQuery(ReactiveJpaParametersParameterAccessor accessor) {

    Mutiny.AbstractQuery countQuery = null;

    String cacheKey = "";
    /*if (namedCountQueryIsPresent) {
      cacheKey = countQueryName;
      countQuery = em.createNamedQuery(countQueryName, Long.class);

    } else {*/

//      String countQueryString = declaredQuery.deriveCountQuery(null, countProjection).getQueryString();
//      cacheKey = countQueryString;
//      countQuery = em.createQuery(countQueryString, Long.class);
//    }

    QueryParameterSetter.QueryMetadata metadata = metadataCache.getMetadata(cacheKey, countQuery);

    return parameterBinder.get().bind(countQuery, metadata, accessor);
  }

  @Override
  protected Class<?> getTypeToRead(ReturnedType returnedType) {
    //TODO
    return returnedType.getTypeToRead();
//    if (getQueryMethod().isNativeQuery()) {
//
//      Class<?> type = returnedType.getReturnedType();
//      Class<?> domainType = returnedType.getDomainType();
//
//      // Domain or subtype -> use return type
//      if (domainType.isAssignableFrom(type)) {
//        return type;
//      }
//
//      // Domain type supertype -> use domain type
//      if (type.isAssignableFrom(domainType)) {
//        return domainType;
//      }
//
//      // Tuples for projection interfaces or explicit SQL mappings for everything else
//      return type.isInterface() ? Tuple.class : null;
//    }
//
//    return declaredQuery.hasConstructorExpression() //
//        ? null //
//        : super.getTypeToRead(returnedType);
  }
}

