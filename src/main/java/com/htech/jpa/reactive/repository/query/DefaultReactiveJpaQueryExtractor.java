package com.htech.jpa.reactive.repository.query;

import org.hibernate.reactive.mutiny.Mutiny;

public class DefaultReactiveJpaQueryExtractor implements ReactiveJpaQueryExtractor {
  @Override
  public String extractQueryString(Mutiny.AbstractQuery query) {
    return null;
  }

  @Override
  public boolean canExtractQuery() {
    return false;
  }
}
