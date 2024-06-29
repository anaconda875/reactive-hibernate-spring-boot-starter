package com.htech.data.jpa.reactive.repository.query;

import java.lang.reflect.Field;
import org.hibernate.reactive.query.ReactiveQuery;
import org.hibernate.reactive.stage.Stage;
import org.hibernate.reactive.stage.impl.StageQueryImpl;
import org.springframework.data.util.ReflectionUtils;

/**
 * @author Bao.Ngo
 */
public class DefaultReactiveJpaQueryExtractor implements ReactiveJpaQueryExtractor {

  @Override
  public String extractQueryString(Stage.AbstractQuery query) {
    if (query instanceof StageQueryImpl<?> sqi) {
      try {
        Field delegate = ReflectionUtils.findRequiredField(StageQueryImpl.class, "delegate");
        org.springframework.util.ReflectionUtils.makeAccessible(delegate);
        ReactiveQuery q = (ReactiveQuery) delegate.get(sqi);
        return q.getQueryString();
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

    throw new IllegalArgumentException("Don't know how to extract the query string from " + query);
  }

  @Override
  public boolean canExtractQuery() {
    return true;
  }
}
