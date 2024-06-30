package com.htech.data.jpa.reactive.repository.support;

import org.springframework.data.jpa.repository.support.CrudMethodMetadata;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class CrudMethodMetadataContextHolder {

  private static final Object KEY = CrudMethodMetadataContextHolder.class;

  private CrudMethodMetadataContextHolder() {}

  public static Mono<CrudMethodMetadata> currentCrudMethodMetadata() {
    return Mono.deferContextual(
        c -> {
          if (c.hasKey(KEY)) {
            return c.get(KEY);
          }
          return Mono.error(
              () -> new IllegalStateException("CrudMethodMetadata may not be correctly setup"));
        });
  }

  public static Context set(Mono<CrudMethodMetadata> metadata) {
    return Context.of(KEY, metadata);
  }
}
