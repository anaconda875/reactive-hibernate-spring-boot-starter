package com.htech.jpa.reactive.repository.query;

import io.smallrye.mutiny.Uni;
import jakarta.persistence.NoResultException;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.jpa.repository.query.*;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.List;

public abstract class ReactiveJpaQueryExecution {

  private static final ConversionService CONVERSION_SERVICE;

  static {

    ConfigurableConversionService conversionService = new DefaultConversionService();

//    conversionService.addConverter(JpaResultConverters.BlobToByteArrayConverter.INSTANCE);
//    conversionService.removeConvertible(Collection.class, Object.class);
//    conversionService.removeConvertible(Object.class, Optional.class);

    CONVERSION_SERVICE = conversionService;
  }


  @Nullable
  public Object execute(AbstractReactiveJpaQuery query, ReactiveJpaParametersParameterAccessor accessor) {

    Assert.notNull(query, "AbstractReactiveJpaQuery must not be null");
    Assert.notNull(accessor, "JpaParametersParameterAccessor must not be null");

    Object result;

    try {
      result = doExecute(query, accessor);
    } catch (NoResultException e) {
      return null;
    }
    
    return result;

//    if (result == null) {
//      return null;
//    }

//    R2dbcQueryMethod queryMethod = query.getQueryMethod();
//    Class<?> requiredType = queryMethod.getReturnType();
//
//    if (ClassUtils.isAssignable(requiredType, void.class) || ClassUtils.isAssignableValue(requiredType, result)) {
//      return result;
//    }
//
//    return CONVERSION_SERVICE.canConvert(result.getClass(), requiredType) //
//        ? CONVERSION_SERVICE.convert(result, requiredType) //
//        : result;
  }

  @Nullable
  protected abstract Object doExecute(AbstractReactiveJpaQuery query, ReactiveJpaParametersParameterAccessor accessor);

  static class CollectionExecution extends ReactiveJpaQueryExecution {

    @Override
    protected Object doExecute(AbstractReactiveJpaQuery query, ReactiveJpaParametersParameterAccessor accessor) {
      return ((Mutiny.SelectionQuery)query.createQuery(accessor)).getResultList();
    }
  }


  /*static class ScrollExecution extends ReactiveJpaQueryExecution {

    private final Sort sort;
    private final ScrollDelegate<?> delegate;

    ScrollExecution(Sort sort, ScrollDelegate<?> delegate) {

      this.sort = sort;
      this.delegate = delegate;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object doExecute(AbstractReactiveJpaQuery query, JpaParametersParameterAccessor accessor) {

      ScrollPosition scrollPosition = accessor.getScrollPosition();
      Mutiny.AbstractQuery scrollQuery = query.createQuery(accessor);

      return delegate.scroll(scrollQuery, sort.and(accessor.getSort()), scrollPosition);
    }
  }*/


 /* static class SlicedExecution extends ReactiveJpaQueryExecution {

    @Override
    @SuppressWarnings("unchecked")
    protected Object doExecute(AbstractReactiveJpaQuery query, JpaParametersParameterAccessor accessor) {

      Pageable pageable = accessor.getPageable();
      Query createQuery = query.createQuery(accessor);

      int pageSize = 0;
      if (pageable.isPaged()) {

        pageSize = pageable.getPageSize();
        createQuery.setMaxResults(pageSize + 1);
      }

      List<Object> resultList = createQuery.getResultList();

      boolean hasNext = pageable.isPaged() && resultList.size() > pageSize;

      return new SliceImpl<>(hasNext ? resultList.subList(0, pageSize) : resultList, pageable, hasNext);

    }
  }*/

  
  /*static class PagedExecution extends ReactiveJpaQueryExecution {

    @Override
    @SuppressWarnings("unchecked")
    protected Object doExecute(AbstractReactiveJpaQuery repositoryQuery, JpaParametersParameterAccessor accessor) {

      Query query = repositoryQuery.createQuery(accessor);

      return PageableExecutionUtils.getPage(query.getResultList(), accessor.getPageable(),
          () -> count(repositoryQuery, accessor));
    }

    private long count(AbstractReactiveJpaQuery repositoryQuery, JpaParametersParameterAccessor accessor) {

      List<?> totals = repositoryQuery.createCountQuery(accessor).getResultList();
      return (totals.size() == 1 ? CONVERSION_SERVICE.convert(totals.get(0), Long.class) : totals.size());
    }
  }*/

  static class SingleEntityExecution extends ReactiveJpaQueryExecution {

    @Override
    protected Object doExecute(AbstractReactiveJpaQuery query, ReactiveJpaParametersParameterAccessor accessor) {

      return ((Mutiny.SelectionQuery<?>)query.createQuery(accessor)).getSingleResult();
    }
  }

  /**
   * Executes a modifying query such as an update, insert or delete.
   */
  static class ModifyingExecution extends ReactiveJpaQueryExecution {

    private final Mutiny.SessionFactory sessionFactory;
//    private final boolean flush;
//    private final boolean clear;


    public ModifyingExecution(ReactiveJpaQueryMethod method, Mutiny.SessionFactory sessionFactory) {

      Assert.notNull(sessionFactory, "The EntityManager must not be null");

//      Class<?> returnType = method.getReturnType();
//
//      boolean isVoid = ClassUtils.isAssignable(returnType, Void.class);
//      boolean isInt = ClassUtils.isAssignable(returnType, Integer.class);
//
//      Assert.isTrue(isInt || isVoid,
//          "Modifying queries can only use void or int/Integer as return type; Offending method: " + method);
//
      this.sessionFactory = sessionFactory;
//      this.flush = method.getFlushAutomatically();
//      this.clear = method.getClearAutomatically();
    }

    @Override
    protected Object doExecute(AbstractReactiveJpaQuery query, ReactiveJpaParametersParameterAccessor accessor) {

//      if (flush) {
//        em.flush();
//      }

      Uni<Integer> result = ((Mutiny.MutationQuery)query.createQuery(accessor)).executeUpdate();

//      if (clear) {
//        em.clear();
//      }

      return result;
    }
  }

  /**
   * {@link ReactiveJpaQueryExecution} removing entities matching the query.
   *
   * @author Thomas Darimont
   * @author Oliver Gierke
   * @since 1.6
   */
  static class DeleteExecution extends ReactiveJpaQueryExecution {

    private final Mutiny.SessionFactory sessionFactory;

    public DeleteExecution(Mutiny.SessionFactory sessionFactory) {
      this.sessionFactory = sessionFactory;
    }

    @Override
    protected Object doExecute(AbstractReactiveJpaQuery jpaQuery, ReactiveJpaParametersParameterAccessor accessor) {
      Mutiny.AbstractQuery query = jpaQuery.createQuery(accessor);
      List<?> resultList = /*query.getResultList();*/ Collections.emptyList();

      for (Object o : resultList) {
        //TODO
//        sessionFactory.remove(o);
      }

      return jpaQuery.getQueryMethod().isCollectionQuery() ? resultList : resultList.size();
    }
  }

  /**
   * {@link ReactiveJpaQueryExecution} performing an exists check on the query.
   *
   * @author Mark Paluch
   * @since 1.11
   */
  static class ExistsExecution extends ReactiveJpaQueryExecution {

    @Override
    protected Object doExecute(AbstractReactiveJpaQuery query, ReactiveJpaParametersParameterAccessor accessor) {
      Uni<List<?>> resultList = ((Mutiny.SelectionQuery) query.createQuery(accessor)).getResultList();
      return resultList.onItem().transform(l -> !l.isEmpty());
    }
  }

  /**
   * {@link ReactiveJpaQueryExecution} executing a stored procedure.
   *
   * @author Thomas Darimont
   * @since 1.6
   */
  static class ProcedureExecution extends ReactiveJpaQueryExecution {

    private final boolean collectionQuery;

    private static final String NO_SURROUNDING_TRANSACTION = "You're trying to execute a @Procedure method without a surrounding transaction that keeps the connection open so that the ResultSet can actually be consumed; Make sure the consumer code uses @Transactional or any other way of declaring a (read-only) transaction";

    ProcedureExecution(boolean collectionQuery) {
      this.collectionQuery = collectionQuery;
    }

    @Override
    protected Object doExecute(AbstractReactiveJpaQuery jpaQuery, ReactiveJpaParametersParameterAccessor accessor) {

      //TODO
      return null;
//      Assert.isInstanceOf(StoredProcedureJpaQuery.class, jpaQuery);
//
//      StoredProcedureJpaQuery query = (StoredProcedureJpaQuery) jpaQuery;
//      StoredProcedureQuery procedure = query.createQuery(accessor);
//
//      try {
//
//        boolean returnsResultSet = procedure.execute();
//
//        if (returnsResultSet) {
//
//          if (!SurroundingTransactionDetectorMethodInterceptor.INSTANCE.isSurroundingTransactionActive()) {
//            throw new InvalidDataAccessApiUsageException(NO_SURROUNDING_TRANSACTION);
//          }
//
//          return collectionQuery ? procedure.getResultList() : procedure.getSingleResult();
//        }
//
//        return query.extractOutputValue(procedure);
//      } finally {
//
//        if (procedure instanceof AutoCloseable ac) {
//          try {
//            ac.close();
//          } catch (Exception ignored) {}
//        }
//      }
    }
  }

  /**
   * {@link ReactiveJpaQueryExecution} executing a Java 8 Stream.
   *
   * @author Thomas Darimont
   * @since 1.8
   */
//  static class StreamExecution extends ReactiveJpaQueryExecution {
//
//    private static final String NO_SURROUNDING_TRANSACTION = "You're trying to execute a streaming query method without a surrounding transaction that keeps the connection open so that the Stream can actually be consumed; Make sure the code consuming the stream uses @Transactional or any other way of declaring a (read-only) transaction";
//
//    private static final Method streamMethod = ReflectionUtils.findMethod(Query.class, "getResultStream");
//
//    @Override
//    protected Object doExecute(AbstractReactiveJpaQuery query, JpaParametersParameterAccessor accessor) {
//
//      if (!SurroundingTransactionDetectorMethodInterceptor.INSTANCE.isSurroundingTransactionActive()) {
//        throw new InvalidDataAccessApiUsageException(NO_SURROUNDING_TRANSACTION);
//      }
//
//      Query jpaQuery = query.createQuery(accessor);
//
//      // JPA 2.2 on the classpath
//      if (streamMethod != null) {
//        return ReflectionUtils.invokeMethod(streamMethod, jpaQuery);
//      }
//
//      // Fall back to legacy stream execution
//      PersistenceProvider persistenceProvider = PersistenceProvider.fromEntityManager(query.getEntityManager());
//      CloseableIterator<Object> iter = persistenceProvider.executeQueryWithResultStream(jpaQuery);
//
//      return StreamUtils.createStreamFromIterator(iter);
//    }
//  }
  
}
