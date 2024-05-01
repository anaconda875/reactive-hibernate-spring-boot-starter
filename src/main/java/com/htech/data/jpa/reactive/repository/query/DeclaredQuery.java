package com.htech.data.jpa.reactive.repository.query;

import java.util.List;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

public interface DeclaredQuery {
  static DeclaredQuery of(@Nullable String query, boolean nativeQuery) {
    return ObjectUtils.isEmpty(query)
        ? EmptyDeclaredQuery.EMPTY_QUERY
        : new StringQuery(query, nativeQuery);
  }

  boolean hasNamedParameter();

  boolean usesJdbcStyleParameters();

  String getQueryString();

  @Nullable
  String getAlias();

  boolean hasConstructorExpression();

  boolean isDefaultProjection();

  List<ParameterBinding> getParameterBindings();

  DeclaredQuery deriveCountQuery(
      @Nullable String countQuery, @Nullable String countQueryProjection);

  default boolean usesPaging() {
    return false;
  }

  default boolean isNativeQuery() {
    return false;
  }
}
