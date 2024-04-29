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

  /**
   * @return whether the underlying query has at least one named parameter.
   */
  boolean hasNamedParameter();

  boolean usesJdbcStyleParameters();

  /** Returns the query string. */
  String getQueryString();

  /**
   * Returns the main alias used in the query.
   *
   * @return the alias
   */
  @Nullable
  String getAlias();

  /**
   * Returns whether the query is using a constructor expression.
   *
   * @since 1.10
   */
  boolean hasConstructorExpression();

  /**
   * Returns whether the query uses the default projection, i.e. returns the main alias defined for
   * the query.
   */
  boolean isDefaultProjection();

  List<ParameterBinding> getParameterBindings();

  /**
   * Creates a new {@literal DeclaredQuery} representing a count query, i.e. a query returning the
   * number of rows to be expected from the original query, either derived from the query wrapped by
   * this instance or from the information passed as arguments.
   *
   * @param countQuery an optional query string to be used if present.
   * @param countQueryProjection an optional return type for the query.
   * @return a new {@literal DeclaredQuery} instance.
   */
  DeclaredQuery deriveCountQuery(
      @Nullable String countQuery, @Nullable String countQueryProjection);

  /**
   * @return whether paging is implemented in the query itself, e.g. using SpEL expressions.
   * @since 2.0.6
   */
  default boolean usesPaging() {
    return false;
  }

  /**
   * Return whether the query is a native query of not.
   *
   * @return <code>true</code> if native query otherwise <code>false</code>
   */
  default boolean isNativeQuery() {
    return false;
  }
}
