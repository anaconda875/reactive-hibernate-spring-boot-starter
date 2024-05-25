package com.htech.jpa.reactive.connection;

import org.hibernate.reactive.stage.Stage;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class SessionContextHolder {

  private static final Object KEY = SessionContextHolder.class;

  private SessionContextHolder() {}

  public static Mono<Stage.Session> currentSession() {
    return Mono.deferContextual(
        c -> {
          if (c.hasKey(KEY)) {
            return c.get(KEY);
          }
          return Mono.error(() -> new IllegalStateException("Session may not be correctly setup"));
        });
  }

  public static Context set(Mono<Stage.Session> session) {
    return Context.of(KEY, session);
  }
}
