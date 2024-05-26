package com.htech.data.jpa.reactive.core;

import com.htech.data.jpa.reactive.mapping.event.BeforeSaveCallback;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.mapping.callback.ReactiveEntityCallbacks;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.publisher.Mono;

import static org.springframework.data.repository.util.ReactiveWrapperConverters.toWrapper;
import static reactor.core.scheduler.Schedulers.DEFAULT_POOL_SIZE;

public class MutinyReactiveJpaEntityTemplate implements MutinyReactiveJpaEntityOperations, ApplicationContextAware {

  private static final ThreadPoolTaskExecutor EXECUTOR;

  private final Mutiny.SessionFactory sessionFactory;
  private ReactiveEntityCallbacks entityCallbacks;

  static {
    EXECUTOR = new ThreadPoolTaskExecutor();
    EXECUTOR.setCorePoolSize(DEFAULT_POOL_SIZE);
    EXECUTOR.setMaxPoolSize(DEFAULT_POOL_SIZE * 2);
    EXECUTOR.setThreadNamePrefix("custom-parallel-");
    EXECUTOR.initialize();
  }

  public MutinyReactiveJpaEntityTemplate(Mutiny.SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public <T> Uni<T> persist(T entity, Mutiny.Session session, Mutiny.Transaction transaction) {
    return doInsert(entity, session, transaction);
  }

  <T> Uni<T> doInsert(T entity, Mutiny.Session session, Mutiny.Transaction transaction) {
    Mono<?> test = Mono.deferContextual(c -> {
      Mono<?> authority = c.<Mono>getOrEmpty("AUTHORITY").orElse(Mono.empty());
      return authority;
    });

    Uni uni = toWrapper(test, Uni.class);

    return toWrapper(maybeCallBeforeSave(entity), Uni.class).flatMap(e -> session
        .persist(e)
        .chain(session::flush)
        .replaceWith(e)
        .emitOn(EXECUTOR)
        .runSubscriptionOn(EXECUTOR));


//    Mono<T> tMono = test.flatMap(v -> {
//      return this.<T>maybeCallBeforeSave(entity);
//    });
//    return tMono.as(e -> {
//      return toWrapper(e, Uni.class).chain(t -> session
//          .persist(t)
//          .chain(session::flush)
//          .replaceWith(t)
//          .emitOn(EXECUTOR)
//          .runSubscriptionOn(EXECUTOR));
//    })
//    ;
  }

  private <T> Mono<T> maybeCallBeforeSave(T entity) {
    if (entityCallbacks != null) {
      return entityCallbacks.callback(BeforeSaveCallback.class, entity);
    }

    return Mono.just(entity);
  }

  @Override
  public Mutiny.SessionFactory sessionFactory() {
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
