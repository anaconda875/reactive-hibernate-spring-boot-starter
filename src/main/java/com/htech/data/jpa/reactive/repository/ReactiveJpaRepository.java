package com.htech.data.jpa.reactive.repository;

import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface ReactiveJpaRepository<T, ID>
    extends CrudRepository<T, ID>, ReactivePagingAndSortingRepository<T, ID> {}
