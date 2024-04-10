package com.htech.jpa.reactive.repository.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.data.jpa.repository.query.InvalidJpaQueryMethodException;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;

public class NativeReactiveJpaQuery extends AbstractStringBasedReactiveJpaQuery {

  public NativeReactiveJpaQuery(ReactiveJpaQueryMethod method, Mutiny.SessionFactory sessionFactory, String queryString, @Nullable String countQueryString,
                                QueryRewriter rewriter, QueryMethodEvaluationContextProvider evaluationContextProvider,
                                SpelExpressionParser parser) {

    super(method, sessionFactory, queryString, countQueryString, rewriter, evaluationContextProvider, parser);

    Parameters<?, ?> parameters = method.getParameters();

    if (parameters.hasSortParameter() && !queryString.contains("#sort")) {
      throw new InvalidJpaQueryMethodException("Cannot use native queries with dynamic sorting in method " + method);
    }
  }

  @Override
  protected Mutiny.AbstractQuery createReactiveJpaQuery(String queryString, ReactiveJpaQueryMethod method, Mutiny.Session session, Sort sort, Pageable pageable, ReturnedType returnedType) {
    Class<?> type = getTypeToQueryFor(returnedType);

    return type == null ? session.createNativeQuery(potentiallyRewriteQuery(queryString, sort, pageable))
        : session.createNativeQuery(potentiallyRewriteQuery(queryString, sort, pageable), type);
  }

  @Nullable
  private Class<?> getTypeToQueryFor(ReturnedType returnedType) {
    Class<?> result = getQueryMethod().isQueryForEntity() ? returnedType.getDomainType() : null;

    if (this.getQuery().hasConstructorExpression() || this.getQuery().isDefaultProjection()) {
      return result;
    }

    return returnedType.isProjecting() && !getMetamodel().isJpaManaged(returnedType.getReturnedType()) //
        ? Tuple.class
        : result;
  }

}
