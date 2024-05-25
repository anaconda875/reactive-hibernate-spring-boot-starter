package com.htech.data.jpa.reactive.repository.query;

import java.util.List;
import java.util.concurrent.CompletionStage;
import org.apache.commons.collections4.CollectionUtils;
import org.hibernate.reactive.stage.Stage;
import org.reactivestreams.Publisher;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class ReactiveJpaQueryExecution {

  private static final ConversionService CONVERSION_SERVICE;

  static {
    ConfigurableConversionService conversionService = new DefaultConversionService();

    //    conversionService.addConverter(JpaResultConverters.BlobToByteArrayConverter.INSTANCE);
    //    conversionService.removeConvertible(Collection.class, Object.class);
    //    conversionService.removeConvertible(Object.class, Optional.class);

    CONVERSION_SERVICE = conversionService;
  }

  //  @Nullable
  public Publisher<?> execute(
      AbstractReactiveJpaQuery query,
      ReactiveJpaParametersParameterAccessor accessor,
      Mono<Stage.Session> session) {
    Assert.notNull(query, "AbstractReactiveJpaQuery must not be null");
    Assert.notNull(accessor, "JpaParametersParameterAccessor must not be null");

    return doExecute(query, accessor, session);

    //    Publisher<?> result;
    //
    //    try {
    //      result = doExecute(query, accessor, session);
    //    } catch (NoResultException e) {
    //      return null;
    //    }
    //
    //    return result;

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

  //  @Nullable
  protected Publisher<?> doExecute(
      AbstractReactiveJpaQuery query,
      ReactiveJpaParametersParameterAccessor accessor,
      Mono<Stage.Session> session) {
    return doExecute(session.map(s -> query.createQuery(accessor, s)));
  }

  protected abstract Publisher<?> doExecute(Mono<Stage.AbstractQuery> query);

  static class CollectionExecution extends ReactiveJpaQueryExecution {

    @Override
    protected Publisher<?> doExecute(Mono<Stage.AbstractQuery> query) {
      return query
          .map(Stage.SelectionQuery.class::cast)
          .flatMap(
              q ->
                  Mono.defer(
                      () -> {
                        CompletionStage<List<?>> resultList = q.getResultList();
                        return Mono.fromCompletionStage(resultList);
                      }))
          .flatMapMany(Flux::fromIterable);
    }

    /*@Override
    protected Publisher<?> doExecute(
        AbstractReactiveJpaQuery query,
        ReactiveJpaParametersParameterAccessor accessor,
        Mono<Stage.Session> session) {
      //      return ((Stage.SelectionQuery) query.createQuery(accessor, session)).getResultList();
      return Flux.usingWhen(
          session,
          s ->
              Mono.fromSupplier(() -> query.createQuery(accessor, s))
                  .map(Stage.SelectionQuery.class::cast)
                  .flatMapMany(
                      q ->
                          Flux.defer(
                              () -> {
                                CompletionStage<List<?>> resultList = q.getResultList();
                                return Mono.fromCompletionStage(resultList)
                                    .flatMapMany(Flux::fromIterable);
                              })),
          s -> Mono.fromCompletionStage(s.close()),
          (s, t) -> Mono.fromCompletionStage(s.close()).then(Mono.error(t)),
          s -> Mono.fromCompletionStage(s.close()));
    }*/
  }

  static class SingleEntityExecution extends ReactiveJpaQueryExecution {

    /*@Override
    protected Publisher<?> doExecute(
        AbstractReactiveJpaQuery query,
        ReactiveJpaParametersParameterAccessor accessor,
        Mono<Stage.Session> session) {
      //      return ((Stage.SelectionQuery<?>) query.createQuery(accessor,
      // session)).getSingleResult();
      return Mono.usingWhen(
          session,
          s ->
              Mono.fromSupplier(() -> query.createQuery(accessor, s))
                  .map(Stage.SelectionQuery.class::cast)
                  .flatMap(q -> Mono.defer(() -> Mono.fromCompletionStage(q.getSingleResult()))),
          s -> Mono.fromCompletionStage(s.close()),
          (s, t) -> Mono.fromCompletionStage(s.close()).then(Mono.error(t)),
          s -> Mono.fromCompletionStage(s.close()));
    }*/

    @Override
    protected Publisher<?> doExecute(Mono<Stage.AbstractQuery> query) {
      return query
          .map(Stage.SelectionQuery.class::cast)
          .flatMap(q -> Mono.defer(() -> Mono.fromCompletionStage(q.getSingleResult())));
    }
  }

  static class ModifyingExecution extends ReactiveJpaQueryExecution {

    //    private final Stage.SessionFactory sessionFactory;

    //    private final boolean flush;
    //    private final boolean clear;

    public ModifyingExecution(
        /*ReactiveJpaQueryMethod method, Stage.SessionFactory sessionFactory*/ ) {
      //      Assert.notNull(sessionFactory, "The sessionFactory must not be null");

      //      Class<?> returnType = method.getReturnType();
      //
      //      boolean isVoid = ClassUtils.isAssignable(returnType, Void.class);
      //      boolean isInt = ClassUtils.isAssignable(returnType, Integer.class);
      //
      //      Assert.isTrue(isInt || isVoid,
      //          "Modifying queries can only use void or int/Integer as return type; Offending
      // method: " + method);
      //
      //      this.sessionFactory = sessionFactory;
      //      this.flush = method.getFlushAutomatically();
      //      this.clear = method.getClearAutomatically();
    }

    /*@Override
    protected Publisher<?> doExecute(
        AbstractReactiveJpaQuery query,
        ReactiveJpaParametersParameterAccessor accessor,
        Mono<Stage.Session> session) {
      return Mono.usingWhen(
          session,
          s ->
              Mono.fromSupplier(() -> query.createQuery(accessor, s))
                  .map(Stage.MutationQuery.class::cast)
                  .flatMap(q -> Mono.defer(() -> Mono.fromCompletionStage(q.executeUpdate()))),
          s -> Mono.fromCompletionStage(s.close()),
          (s, t) -> Mono.fromCompletionStage(s.close()).then(Mono.error(t)),
          s -> Mono.fromCompletionStage(s.close()));
      //      if (flush) {
      //        em.flush();
      //      }

      //      Uni<Integer> result = ((Stage.MutationQuery) query.createQuery(accessor,
      // session)).executeUpdate();

      //      if (clear) {
      //        em.clear();
      //      }

      //      return result;
    }*/

    @Override
    protected Publisher<?> doExecute(Mono<Stage.AbstractQuery> query) {
      return query
          .map(Stage.MutationQuery.class::cast)
          .flatMap(q -> Mono.defer(() -> Mono.fromCompletionStage(q.executeUpdate())));
    }
  }

  static class DeleteExecution extends ReactiveJpaQueryExecution {

    private final Stage.SessionFactory sessionFactory;

    public DeleteExecution(Stage.SessionFactory sessionFactory) {
      this.sessionFactory = sessionFactory;
    }

    /*@Override
    protected Publisher<?> doExecute(
        AbstractReactiveJpaQuery jpaQuery,
        ReactiveJpaParametersParameterAccessor accessor,
        Mono<Stage.Session> session) {
      return Mono.usingWhen(
          session,
          s ->
              Mono.fromSupplier(() -> jpaQuery.createQuery(accessor, s))
                  .map(Stage.MutationQuery.class::cast)
                  .flatMap(q -> Mono.defer(() -> Mono.fromCompletionStage(q.executeUpdate()))),
          s -> Mono.fromCompletionStage(s.close()),
          (s, t) -> Mono.fromCompletionStage(s.close()).then(Mono.error(t)),
          s -> Mono.fromCompletionStage(s.close()));

      //      return jpaQuery.createQuery(accessor, session).map(Stage.MutationQuery.class::cast)
      //          .flatMap(q -> Mono.defer(() -> Mono.fromCompletionStage(q.executeUpdate())));
      //      return query.executeUpdate();
    }*/

    @Override
    protected Publisher<?> doExecute(Mono<Stage.AbstractQuery> query) {
      return query
          .map(Stage.MutationQuery.class::cast)
          .flatMap(q -> Mono.defer(() -> Mono.fromCompletionStage(q.executeUpdate())));
    }
  }

  static class ExistsExecution extends ReactiveJpaQueryExecution {

    /*@Override
    protected Publisher<?> doExecute(
        AbstractReactiveJpaQuery query,
        ReactiveJpaParametersParameterAccessor accessor,
        Mono<Stage.Session> session) {
      */
    /*Uni<List<?>> resultList =
        ((Stage.SelectionQuery) query.createQuery(accessor, session)).getResultList();
    return resultList.onItem().transform(l -> !l.isEmpty());*/
    /*
      //      return query.createQuery(accessor, session).map(Stage.SelectionQuery.class::cast)
      //          .<List<?>>flatMap(q -> Mono.defer(() ->
      // Mono.fromCompletionStage(q.getResultList())))
      //          .map(CollectionUtils::isNotEmpty);

      return Mono.usingWhen(
          session,
          s ->
              Mono.fromSupplier(() -> query.createQuery(accessor, s))
                  .map(Stage.SelectionQuery.class::cast)
                  .flatMap(
                      q ->
                          Mono.defer(
                              () -> {
                                CompletionStage<List<?>> resultList = q.getResultList();
                                return Mono.fromCompletionStage(resultList);
                              }))
                  .map(CollectionUtils::isNotEmpty),
          s -> Mono.fromCompletionStage(s.close()),
          (s, t) -> Mono.fromCompletionStage(s.close()).then(Mono.error(t)),
          s -> Mono.fromCompletionStage(s.close()));
    }*/

    @Override
    protected Publisher<?> doExecute(Mono<Stage.AbstractQuery> query) {
      return query
          .map(Stage.SelectionQuery.class::cast)
          .flatMap(
              q ->
                  Mono.defer(
                      () -> {
                        CompletionStage<List<?>> resultList = q.getResultList();
                        return Mono.fromCompletionStage(resultList);
                      }))
          .map(CollectionUtils::isNotEmpty);
    }
  }

  static class ProcedureExecution extends ReactiveJpaQueryExecution {

    private final boolean collectionQuery;

    private static final String NO_SURROUNDING_TRANSACTION =
        "You're trying to execute a @Procedure method without a surrounding transaction that keeps the connection open so that the ResultSet can actually be consumed; Make sure the consumer code uses @Transactional or any other way of declaring a (read-only) transaction";

    ProcedureExecution(boolean collectionQuery) {
      this.collectionQuery = collectionQuery;
    }

    /*@Override
    protected Publisher<?> doExecute(
        AbstractReactiveJpaQuery jpaQuery,
        ReactiveJpaParametersParameterAccessor accessor,
        Mono<Stage.Session> session) {

      // TODO
      return null;
    }*/

    @Override
    protected Publisher<?> doExecute(Mono<Stage.AbstractQuery> query) {
      // TODO
      return null;
    }
  }
}
