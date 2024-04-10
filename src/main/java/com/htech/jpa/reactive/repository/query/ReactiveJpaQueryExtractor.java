package com.htech.jpa.reactive.repository.query;

import jakarta.persistence.Query;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.data.jpa.provider.QueryExtractor;

public interface ReactiveJpaQueryExtractor {
  String extractQueryString(Mutiny.AbstractQuery query);

  boolean canExtractQuery();
}
