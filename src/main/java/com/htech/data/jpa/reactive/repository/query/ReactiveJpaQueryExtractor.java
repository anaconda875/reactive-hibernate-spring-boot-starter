package com.htech.data.jpa.reactive.repository.query;

import org.hibernate.reactive.stage.Stage;

public interface ReactiveJpaQueryExtractor {
  String extractQueryString(Stage.AbstractQuery query);

  boolean canExtractQuery();
}
