package com.htech.data.jpa.reactive.core;

import org.hibernate.reactive.stage.Stage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StageReactiveJpaEntityOperations {

  <T> Mono<T> persist(T entity);

  <T> Flux<T> persist(Iterable<T> entity);

  Stage.SessionFactory sessionFactory();
}
