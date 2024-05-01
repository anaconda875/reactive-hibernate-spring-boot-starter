package com.htech.data.jpa.reactive.repository.query;

import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.lang.Nullable;

public interface QueryEnhancer {

  default String applySorting(Sort sort) {
    return applySorting(sort, detectAlias());
  }

  String applySorting(Sort sort, @Nullable String alias);

  @Nullable
  String detectAlias();

  default String createCountQueryFor() {
    return createCountQueryFor(null);
  }

  String createCountQueryFor(@Nullable String countProjection);

  default boolean hasConstructorExpression() {
    return QueryUtils.hasConstructorExpression(getQuery().getQueryString());
  }

  String getProjection();

  Set<String> getJoinAliases();

  DeclaredQuery getQuery();
}
