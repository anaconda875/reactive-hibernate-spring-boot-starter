package com.htech.jpa.reactive.repository;

import io.smallrye.mutiny.Uni;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@NoRepositoryBean
public interface ReactiveJpaRepository<T, ID> extends Repository<T, ID> {

  Flux<T> findAll();

//  Uni<T> findById(ID id);
  Mono<T> findById(ID id);

  @Transactional
  @Modifying
  Mono<T> save(T entity);
}
