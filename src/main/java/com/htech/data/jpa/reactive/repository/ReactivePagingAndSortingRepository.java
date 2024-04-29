package com.htech.data.jpa.reactive.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import reactor.core.publisher.Flux;

@NoRepositoryBean
public interface ReactivePagingAndSortingRepository<T, ID> extends Repository<T, ID> {

  Flux<T> findAll(Sort sort);

  Flux<T> findAll(Pageable pageable);
}
