package com.htech.data.jpa.reactive.repository.query;

import org.hibernate.reactive.stage.Stage;

/**
 * @author Bao.Ngo
 */
public interface ReactiveJpaQueryExtractor {
  String extractQueryString(Stage.AbstractQuery query);

  boolean canExtractQuery();
}
