package com.htech.data.jpa.reactive.repository.support;

import com.htech.data.jpa.reactive.repository.ReactiveJpaRepository;
import com.htech.data.jpa.reactive.repository.ReactiveJpaSpecificationExecutor;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * @author Bao.Ngo
 */
@NoRepositoryBean
public interface ReactiveJpaRepositoryImplementation<T, ID>
    extends ReactiveJpaRepository<T, ID>, ReactiveJpaSpecificationExecutor<T> {

  default void setEscapeCharacter(EscapeCharacter escapeCharacter) {}
}
