package com.htech.data.jpa.reactive.repository.query;

import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;

/**
 * @author Bao.Ngo
 */
public class DefaultQueryEnhancer implements QueryEnhancer {

  protected final DeclaredQuery query;
  protected final boolean hasConstructorExpression;
  protected final String alias;
  protected final String projection;
  protected final Set<String> joinAliases;

  public DefaultQueryEnhancer(DeclaredQuery query) {
    this.query = query;
    this.hasConstructorExpression = QueryUtils.hasConstructorExpression(query.getQueryString());
    this.alias = QueryUtils.detectAlias(query.getQueryString());
    this.projection = QueryUtils.getProjection(this.query.getQueryString());
    this.joinAliases = QueryUtils.getOuterJoinAliases(this.query.getQueryString());
  }

  @Override
  public String applySorting(Sort sort, @Nullable String alias) {
    return QueryUtils.applySorting(this.query.getQueryString(), sort, alias);
  }

  @Override
  public String createCountQueryFor(@Nullable String countProjection) {
    return QueryUtils.createCountQueryFor(
        this.query.getQueryString(), countProjection, this.query.isNativeQuery());
  }

  @Override
  public boolean hasConstructorExpression() {
    return this.hasConstructorExpression;
  }

  @Override
  public String detectAlias() {
    return this.alias;
  }

  @Override
  public String getProjection() {
    return this.projection;
  }

  @Override
  public Set<String> getJoinAliases() {
    return this.joinAliases;
  }

  @Override
  public DeclaredQuery getQuery() {
    return this.query;
  }
}
