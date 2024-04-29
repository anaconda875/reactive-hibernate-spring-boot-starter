package com.htech.data.jpa.reactive.repository.query;

import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.lang.Nullable;

public interface QueryEnhancer {

  default String applySorting(Sort sort) {
    return applySorting(sort, detectAlias());
  }

  /**
   * Adds {@literal order by} clause to the JPQL query.
   *
   * @param sort the sort specification to apply.
   * @param alias the alias to be used in the order by clause. May be {@literal null} or empty.
   * @return the modified query string.
   */
  String applySorting(Sort sort, @Nullable String alias);

  /**
   * Resolves the alias for the entity to be retrieved from the given JPA query.
   *
   * @return Might return {@literal null}.
   */
  @Nullable
  String detectAlias();

  /**
   * Creates a count projected query from the given original query.
   *
   * @return Guaranteed to be not {@literal null}.
   */
  default String createCountQueryFor() {
    return createCountQueryFor(null);
  }

  /**
   * Creates a count projected query from the given original query using the provided <code>
   * countProjection</code>.
   *
   * @param countProjection may be {@literal null}.
   * @return a query String to be used a count query for pagination. Guaranteed to be not {@literal
   *     null}.
   */
  String createCountQueryFor(@Nullable String countProjection);

  /**
   * Returns whether the given JPQL query contains a constructor expression.
   *
   * @return whether the given JPQL query contains a constructor expression.
   */
  default boolean hasConstructorExpression() {
    return QueryUtils.hasConstructorExpression(getQuery().getQueryString());
  }

  /**
   * Returns the projection part of the query, i.e. everything between {@code select} and {@code
   * from}.
   *
   * @return the projection part of the query.
   */
  String getProjection();

  Set<String> getJoinAliases();

  DeclaredQuery getQuery();
}
