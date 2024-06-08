package com.htech.data.jpa.reactive.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@NoRepositoryBean
public interface CrudRepository<T, ID> extends Repository<T, ID> {
  <S extends T> Flux<S> findAll();

  //  Uni<T> findById(ID id);
  <S extends T> Mono<S> findById(ID id);

  Mono<T> getReferenceById(ID id);

  @Transactional
  @Modifying
  <S extends T> Mono<S> save(S entity);

  @Transactional
  <S extends T> Flux<S> saveAll(Iterable<S> entities);

  Mono<Boolean> existsById(ID id);

  <S extends T> Flux<S> findAllById(Iterable<ID> ids);

  Mono<Long> count();

  @Transactional
  Mono<Void> deleteById(ID id);

  @Transactional
  Mono<Void> delete(T entity);

  @Transactional
  Mono<Void> deleteAllById(Iterable<? extends ID> ids);

  @Transactional
  Mono<Void> deleteAll(Iterable<? extends T> entities);

  @Transactional
  Mono<Void> deleteAll();
}
