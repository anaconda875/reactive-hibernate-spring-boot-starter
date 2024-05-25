package com.htech.data.jpa.reactive.repository.query;

import org.hibernate.reactive.stage.Stage;

public class DefaultReactiveJpaQueryExtractor implements ReactiveJpaQueryExtractor {
  @Override
  public String extractQueryString(Stage.AbstractQuery query) {
    return null;
  }

  @Override
  public boolean canExtractQuery() {
    return false;
  }
}
