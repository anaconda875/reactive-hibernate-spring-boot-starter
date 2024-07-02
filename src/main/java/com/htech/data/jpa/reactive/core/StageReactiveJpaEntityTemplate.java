package com.htech.data.jpa.reactive.core;

import static reactor.core.scheduler.Schedulers.DEFAULT_POOL_SIZE;

import com.htech.data.jpa.reactive.mapping.event.BeforeSaveCallback;
import com.htech.jpa.reactive.connection.SessionContextHolder;
import org.apache.commons.collections4.IterableUtils;
import org.hibernate.reactive.stage.Stage;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.mapping.callback.ReactiveEntityCallbacks;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Bao.Ngo
 */
public class StageReactiveJpaEntityTemplate
    implements StageReactiveJpaEntityOperations, ApplicationContextAware {

  private static final ThreadPoolTaskExecutor EXECUTOR;

  private final Stage.SessionFactory sessionFactory;
  private ReactiveEntityCallbacks entityCallbacks;

  static {
    EXECUTOR = new ThreadPoolTaskExecutor();
    EXECUTOR.setCorePoolSize(DEFAULT_POOL_SIZE);
    EXECUTOR.setMaxPoolSize(DEFAULT_POOL_SIZE * 2);
    EXECUTOR.setThreadNamePrefix("custom-parallel-");
    EXECUTOR.initialize();
  }

  public StageReactiveJpaEntityTemplate(Stage.SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public <T> Mono<T> persist(T entity) {
    return doInsert(entity);
  }

  @Override
  public <T> Flux<T> persist(Iterable<T> entities) {
    return doInsert(entities);
  }

  private <T> Flux<T> doInsert(Iterable<T> entities) {
    if (IterableUtils.isEmpty(entities)) {
      return Flux.empty();
    }

    return SessionContextHolder.currentSession()
        .flatMap(
            session ->
                Flux.fromIterable(entities)
                    .concatMap(this::maybeCallBeforeSave)
                    .collectList()
                    .flatMap(
                        list ->
                            Mono.defer(
                                () ->
                                    Mono.fromCompletionStage(session.persist(list.toArray()))
                                        .then(deferFlushing(session))
                                        .thenReturn(list))))
        .flatMapMany(Flux::fromIterable);
  }

  private <T> Mono<T> doInsert(T entity) {
    return maybeCallBeforeSave(entity)
        .flatMap(
            e ->
                SessionContextHolder.currentSession()
                    .flatMap(
                        session ->
                            Mono.defer(
                                () ->
                                    Mono.fromCompletionStage(session.persist(e))
                                        .then(deferFlushing(session))
                                        .thenReturn(e))));
  }

  private <T> Mono<T> maybeCallBeforeSave(T entity) {
    if (entityCallbacks != null) {
      return entityCallbacks.callback(BeforeSaveCallback.class, entity);
    }

    return Mono.just(entity);
  }

  private static Mono<Void> deferFlushing(Stage.Session session) {
    return Mono.defer(() -> Mono.fromCompletionStage(session.flush()));
  }

  @Override
  public Stage.SessionFactory sessionFactory() {
    return sessionFactory;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    if (entityCallbacks == null) {
      setEntityCallbacks(ReactiveEntityCallbacks.create(applicationContext));
    }
  }

  public void setEntityCallbacks(ReactiveEntityCallbacks entityCallbacks) {
    this.entityCallbacks = entityCallbacks;
  }
}
