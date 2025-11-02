package com.htech.data.jpa.reactive.repository.query;

import java.util.List;
import java.util.concurrent.CompletionStage;
import org.apache.commons.collections4.CollectionUtils;
import org.hibernate.reactive.stage.Stage;
import org.reactivestreams.Publisher;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Bao.Ngo
 */
public abstract class ReactiveJpaQueryExecution {

  private static final ConversionService CONVERSION_SERVICE;

  static {
    ConfigurableConversionService conversionService = new DefaultConversionService();

    /*conversionService.addConverter(JpaResultConverters.BlobToByteArrayConverter.INSTANCE);
    conversionService.removeConvertible(Collection.class, Object.class);
    conversionService.removeConvertible(Object.class, Optional.class);*/

    CONVERSION_SERVICE = conversionService;
  }

  public Publisher<?> execute(
      AbstractReactiveJpaQuery query,
      ReactiveJpaParametersParameterAccessor accessor,
      Mono<Stage.Session> session) {
    Assert.notNull(query, "AbstractReactiveJpaQuery must not be null");
    Assert.notNull(accessor, "JpaParametersParameterAccessor must not be null");

    return doExecute(query, accessor, session);
  }

  protected Publisher<?> doExecute(
      AbstractReactiveJpaQuery query,
      ReactiveJpaParametersParameterAccessor accessor,
      Mono<Stage.Session> session) {
    return doExecute(query.createQuery(session, accessor), query, accessor, session);
  }

  protected abstract Publisher<?> doExecute(
      Mono<Stage.AbstractQuery> query,
      AbstractReactiveJpaQuery reactiveJpaQuery,
      ReactiveJpaParametersParameterAccessor accessor,
      Mono<Stage.Session> session);

  static class CollectionExecution extends ReactiveJpaQueryExecution {

    @Override
    protected Publisher<?> doExecute(
        Mono<Stage.AbstractQuery> query,
        AbstractReactiveJpaQuery reactiveJpaQuery,
        ReactiveJpaParametersParameterAccessor accessor,
        Mono<Stage.Session> session) {
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
  }

  static class SingleEntityExecution extends ReactiveJpaQueryExecution {

    @Override
    protected Publisher<?> doExecute(
        Mono<Stage.AbstractQuery> query,
        AbstractReactiveJpaQuery reactiveJpaQuery,
        ReactiveJpaParametersParameterAccessor accessor,
        Mono<Stage.Session> session) {
      return query
          .map(Stage.SelectionQuery.class::cast)
          .flatMap(q -> Mono.defer(() -> Mono.fromCompletionStage(q.getSingleResult())));
    }
  }

  static class PagedExecution extends ReactiveJpaQueryExecution {

    @Override
    protected Publisher<?> doExecute(
        Mono<Stage.AbstractQuery> query,
        AbstractReactiveJpaQuery reactiveJpaQuery,
        ReactiveJpaParametersParameterAccessor accessor,
        Mono<Stage.Session> session) {
      return query
          .map(Stage.SelectionQuery.class::cast)
          .flatMap(
              q -> {
                CompletionStage<List<?>> list = q.getResultList();
                return Mono.fromCompletionStage(list);
              })
          .zipWhen(
              __ ->
                  reactiveJpaQuery
                      .createCountQuery(session, accessor)
                      .map(Stage.SelectionQuery.class::cast)
                      .flatMap(
                          q -> {
                            CompletionStage<List<Long>> resultList = q.getResultList();
                            return Mono.fromCompletionStage(resultList);
                          })
                      .map(l -> l.stream().reduce(0L, Long::sum)))
          .map(t -> PageableExecutionUtils.getPage(t.getT1(), accessor.getPageable(), t::getT2));
    }
  }

  static class ModifyingExecution extends ReactiveJpaQueryExecution {

    /*    private final Stage.SessionFactory sessionFactory;

    private final boolean flush;
    private final boolean clear;*/

    public ModifyingExecution(
        /*ReactiveJpaQueryMethod method, Stage.SessionFactory sessionFactory*/ ) {}

    @Override
    protected Publisher<?> doExecute(
        Mono<Stage.AbstractQuery> query,
        AbstractReactiveJpaQuery reactiveJpaQuery,
        ReactiveJpaParametersParameterAccessor accessor,
        Mono<Stage.Session> session) {
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

    @Override
    protected Publisher<?> doExecute(
        Mono<Stage.AbstractQuery> query,
        AbstractReactiveJpaQuery reactiveJpaQuery,
        ReactiveJpaParametersParameterAccessor accessor,
        Mono<Stage.Session> session) {
      return query
          .map(Stage.MutationQuery.class::cast)
          .flatMap(q -> Mono.defer(() -> Mono.fromCompletionStage(q.executeUpdate())));
    }
  }

  static class ExistsExecution extends ReactiveJpaQueryExecution {

    @Override
    protected Publisher<?> doExecute(
        Mono<Stage.AbstractQuery> query,
        AbstractReactiveJpaQuery reactiveJpaQuery,
        ReactiveJpaParametersParameterAccessor accessor,
        Mono<Stage.Session> session) {
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
    protected Publisher<?> doExecute(
        Mono<Stage.AbstractQuery> query,
        AbstractReactiveJpaQuery reactiveJpaQuery,
        ReactiveJpaParametersParameterAccessor accessor,
        Mono<Stage.Session> session) {
      // TODO
      return null;
    }
  }
}
