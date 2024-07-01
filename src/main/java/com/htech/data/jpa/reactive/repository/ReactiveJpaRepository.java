package com.htech.data.jpa.reactive.repository;

import org.springframework.data.repository.NoRepositoryBean;

/**
 * @author Bao.Ngo
 */
@NoRepositoryBean
public interface ReactiveJpaRepository<T, ID>
    extends CrudRepository<T, ID>, ReactivePagingAndSortingRepository<T, ID> {}
