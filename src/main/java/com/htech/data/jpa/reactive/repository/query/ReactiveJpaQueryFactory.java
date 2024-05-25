package com.htech.data.jpa.reactive.repository.query;

import org.hibernate.reactive.stage.Stage;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.data.repository.query.QueryCreationException;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;

public enum ReactiveJpaQueryFactory {
  INSTANCE;

  private static final SpelExpressionParser PARSER = new SpelExpressionParser();

  AbstractReactiveJpaQuery fromMethodWithQueryString(
      ReactiveJpaQueryMethod method,
      Stage.SessionFactory sessionFactory,
      String queryString,
      @Nullable String countQueryString,
      QueryRewriter queryRewriter,
      QueryMethodEvaluationContextProvider evaluationContextProvider) {

    if (method.isScrollQuery()) {
      throw QueryCreationException.create(
          method, "Scroll queries are not supported using String-based queries");
    }

    return method.isNativeQuery()
        ? new NativeReactiveJpaQuery(
            method,
            sessionFactory,
            queryString,
            countQueryString,
            queryRewriter,
            evaluationContextProvider,
            PARSER)
        : new SimpleReactiveJpaQuery(
            method,
            sessionFactory,
            queryString,
            countQueryString,
            queryRewriter,
            evaluationContextProvider,
            PARSER);
  }

  // TODO
  /* public StoredProcedureJpaQuery fromProcedureAnnotation(JpaQueryMethod method, EntityManager em) {

    if (method.isScrollQuery()) {
      throw QueryCreationException.create(method, "Scroll queries are not supported using stored procedures");
    }

    return new StoredProcedureJpaQuery(method, em);
  }*/
}
