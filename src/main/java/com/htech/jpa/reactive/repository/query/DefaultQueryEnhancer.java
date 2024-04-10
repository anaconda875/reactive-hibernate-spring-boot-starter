package com.htech.jpa.reactive.repository.query;

import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;

import java.util.Set;

/**
 * For native query
 */
public class DefaultQueryEnhancer implements QueryEnhancer {

  protected final DeclaredQuery query;

  public DefaultQueryEnhancer(DeclaredQuery query) {
    this.query = query;
  }

  @Override
  public String applySorting(Sort sort, @Nullable String alias) {
    return QueryUtils.applySorting(this.query.getQueryString(), sort, alias);
  }

  @Override
  public String detectAlias() {
    return QueryUtils.detectAlias(this.query.getQueryString());
  }

  @Override
  public String createCountQueryFor(@Nullable String countProjection) {
    return QueryUtils.createCountQueryFor(this.query.getQueryString(), countProjection, this.query.isNativeQuery());
  }

  @Override
  public String getProjection() {
    return QueryUtils.getProjection(this.query.getQueryString());
  }

  @Override
  public Set<String> getJoinAliases() {
    return QueryUtils.getOuterJoinAliases(this.query.getQueryString());
  }

  @Override
  public DeclaredQuery getQuery() {
    return this.query;
  }
}
