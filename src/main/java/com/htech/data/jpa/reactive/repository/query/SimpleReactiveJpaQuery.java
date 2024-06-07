package com.htech.data.jpa.reactive.repository.query;

import org.hibernate.reactive.stage.Stage;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.data.repository.query.ReactiveExtensionAwareQueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.ReactiveQueryMethodEvaluationContextProvider;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;

public class SimpleReactiveJpaQuery extends AbstractStringBasedReactiveJpaQuery {

  public SimpleReactiveJpaQuery(
      ReactiveJpaQueryMethod method,
      Stage.SessionFactory sessionFactory,
      @Nullable String countQueryString,
      QueryRewriter queryRewriter,
      ReactiveExtensionAwareQueryMethodEvaluationContextProvider evaluationContextProvider,
      SpelExpressionParser parser) {
    this(
        method,
        sessionFactory,
        method.getRequiredAnnotatedQuery(),
        countQueryString,
        queryRewriter,
        evaluationContextProvider,
        parser);
  }

  public SimpleReactiveJpaQuery(
      ReactiveJpaQueryMethod method,
      Stage.SessionFactory sessionFactory,
      String queryString,
      @Nullable String countQueryString,
      QueryRewriter queryRewriter,
      ReactiveQueryMethodEvaluationContextProvider evaluationContextProvider,
      SpelExpressionParser parser) {

    super(
        method,
        sessionFactory,
        queryString,
        countQueryString,
        queryRewriter,
        evaluationContextProvider,
        parser);

    // TODO
    //    validateQuery(getQuery().getQueryString(), "Validation failed for query for method %s",
    // method);

    /*if (method.isPageQuery()) {
      validateQuery(getCountQuery().getQueryString(),
          String.format("Count query validation failed for method %s", method));
    }*/
  }

  private void validateQuery(String query, String errorMessage, Object... arguments) {

    if (getQueryMethod().isProcedureQuery()) {
      return;
    }

    try {

    } catch (RuntimeException e) {
      // Needed as there's ambiguities in how an invalid query string shall be expressed by the
      // persistence provider
      // https://java.net/projects/jpa-spec/lists/jsr338-experts/archive/2012-07/message/17
      throw new IllegalArgumentException(String.format(errorMessage, arguments), e);
    }
  }
}
