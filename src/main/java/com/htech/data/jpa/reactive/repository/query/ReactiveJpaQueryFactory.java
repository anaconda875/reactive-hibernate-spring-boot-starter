package com.htech.data.jpa.reactive.repository.query;

import org.hibernate.reactive.stage.Stage;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.data.repository.query.QueryCreationException;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.lang.Nullable;

/**
 * @author Bao.Ngo
 */
public enum ReactiveJpaQueryFactory {
  INSTANCE;

  AbstractReactiveJpaQuery fromMethodWithQueryString(
      ReactiveJpaQueryMethod method,
      Stage.SessionFactory sessionFactory,
      String queryString,
      @Nullable String countQueryString,
      QueryRewriter queryRewriter,
      ValueExpressionDelegate delegate) {

    if (method.isScrollQuery()) {
      throw QueryCreationException.create(
          method, "Scroll queries are not supported using String-based queries");
    }

    return method.isNativeQuery()
        ? new NativeReactiveJpaQuery(
            method, sessionFactory, queryString, countQueryString, queryRewriter, delegate)
        : new SimpleReactiveJpaQuery(
            method, sessionFactory, queryString, countQueryString, queryRewriter, delegate);
  }

  // TODO
  /* public StoredProcedureJpaQuery fromProcedureAnnotation(JpaQueryMethod method, EntityManager em) {

    if (method.isScrollQuery()) {
      throw QueryCreationException.create(method, "Scroll queries are not supported using stored procedures");
    }

    return new StoredProcedureJpaQuery(method, em);
  }*/
}
