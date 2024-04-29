package com.htech.data.jpa.reactive.repository.query;

import org.hibernate.reactive.mutiny.Mutiny;

public interface ReactiveJpaQueryExtractor {
  String extractQueryString(Mutiny.AbstractQuery query);

  boolean canExtractQuery();
}
