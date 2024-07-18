package com.htech.data.jpa.reactive.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReactiveJpaSpecificationExecutor<T> {

  Flux<T> findAll(Specification<T> spec);

  Mono<List<T>> findAllToList(Specification<T> spec);

  Mono<Page<T>> findAll(Specification<T> spec, Pageable pageable);
}
