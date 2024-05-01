package com.htech.data.jpa.reactive.repository.query;

import io.smallrye.mutiny.Uni;
import jakarta.persistence.NoResultException;
import java.util.List;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

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
  public Object execute(
      AbstractReactiveJpaQuery query, ReactiveJpaParametersParameterAccessor accessor) {

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
    //    if (ClassUtils.isAssignable(requiredType, void.class) ||
    // ClassUtils.isAssignableValue(requiredType, result)) {
    //      return result;
    //    }
    //
    //    return CONVERSION_SERVICE.canConvert(result.getClass(), requiredType) //
    //        ? CONVERSION_SERVICE.convert(result, requiredType) //
    //        : result;
  }

  @Nullable
  protected abstract Object doExecute(
      AbstractReactiveJpaQuery query, ReactiveJpaParametersParameterAccessor accessor);

  static class CollectionExecution extends ReactiveJpaQueryExecution {

    @Override
    protected Object doExecute(
        AbstractReactiveJpaQuery query, ReactiveJpaParametersParameterAccessor accessor) {
      return ((Mutiny.SelectionQuery) query.createQuery(accessor)).getResultList();
    }
  }

  static class SingleEntityExecution extends ReactiveJpaQueryExecution {

    @Override
    protected Object doExecute(
        AbstractReactiveJpaQuery query, ReactiveJpaParametersParameterAccessor accessor) {

      return ((Mutiny.SelectionQuery<?>) query.createQuery(accessor)).getSingleResult();
    }
  }

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
      //          "Modifying queries can only use void or int/Integer as return type; Offending
      // method: " + method);
      //
      this.sessionFactory = sessionFactory;
      //      this.flush = method.getFlushAutomatically();
      //      this.clear = method.getClearAutomatically();
    }

    @Override
    protected Object doExecute(
        AbstractReactiveJpaQuery query, ReactiveJpaParametersParameterAccessor accessor) {

      //      if (flush) {
      //        em.flush();
      //      }

      Uni<Integer> result = ((Mutiny.MutationQuery) query.createQuery(accessor)).executeUpdate();

      //      if (clear) {
      //        em.clear();
      //      }

      return result;
    }
  }

  static class DeleteExecution extends ReactiveJpaQueryExecution {

    private final Mutiny.SessionFactory sessionFactory;

    public DeleteExecution(Mutiny.SessionFactory sessionFactory) {
      this.sessionFactory = sessionFactory;
    }

    @Override
    protected Object doExecute(
        AbstractReactiveJpaQuery jpaQuery, ReactiveJpaParametersParameterAccessor accessor) {
      Mutiny.MutationQuery query = (Mutiny.MutationQuery) jpaQuery.createQuery(accessor);

      return query.executeUpdate();
    }
  }

  static class ExistsExecution extends ReactiveJpaQueryExecution {

    @Override
    protected Object doExecute(
        AbstractReactiveJpaQuery query, ReactiveJpaParametersParameterAccessor accessor) {
      Uni<List<?>> resultList =
          ((Mutiny.SelectionQuery) query.createQuery(accessor)).getResultList();
      return resultList.onItem().transform(l -> !l.isEmpty());
    }
  }

  static class ProcedureExecution extends ReactiveJpaQueryExecution {

    private final boolean collectionQuery;

    private static final String NO_SURROUNDING_TRANSACTION =
        "You're trying to execute a @Procedure method without a surrounding transaction that keeps the connection open so that the ResultSet can actually be consumed; Make sure the consumer code uses @Transactional or any other way of declaring a (read-only) transaction";

    ProcedureExecution(boolean collectionQuery) {
      this.collectionQuery = collectionQuery;
    }

    @Override
    protected Object doExecute(
        AbstractReactiveJpaQuery jpaQuery, ReactiveJpaParametersParameterAccessor accessor) {

      // TODO
      return null;
    }
  }
}
