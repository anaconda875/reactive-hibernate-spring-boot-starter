package com.htech.data.jpa.reactive.repository.query;

import java.util.Collections;
import java.util.List;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public class EmptyDeclaredQuery implements DeclaredQuery {

  /**
   * An implementation implementing the NULL-Object pattern for situations where there is no query.
   */
  static final DeclaredQuery EMPTY_QUERY = new EmptyDeclaredQuery();

  @Override
  public boolean hasNamedParameter() {
    return false;
  }

  @Override
  public boolean usesJdbcStyleParameters() {
    return false;
  }

  @Override
  public String getQueryString() {
    return "";
  }

  @Override
  public String getAlias() {
    return null;
  }

  @Override
  public boolean hasConstructorExpression() {
    return false;
  }

  @Override
  public boolean isDefaultProjection() {
    return false;
  }

  @Override
  public List<ParameterBinding> getParameterBindings() {
    return Collections.emptyList();
  }

  @Override
  public DeclaredQuery deriveCountQuery(
      @Nullable String countQuery, @Nullable String countQueryProjection) {

    Assert.hasText(countQuery, "CountQuery must not be empty");

    return DeclaredQuery.of(countQuery, false);
  }
}
