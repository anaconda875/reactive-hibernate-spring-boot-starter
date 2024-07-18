package com.htech.data.jpa.reactive.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactiveJpaSpecificationExecutor<T> {

  Mono<T> findOne(Specification<T> spec);

  Flux<T> findAll(Specification<T> spec);

  Flux<T> findAll(Specification<T> spec, Sort sort);

  Mono<List<T>> findAllToList(Specification<T> spec);

  Mono<Page<T>> findAll(Specification<T> spec, Pageable pageable);

  Mono<Long> count(Specification<T> spec);

  Mono<Boolean> exists(Specification<T> spec);

  Mono<Long> delete(Specification<T> spec);
}
