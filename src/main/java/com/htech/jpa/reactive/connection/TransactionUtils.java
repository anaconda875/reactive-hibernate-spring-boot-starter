package com.htech.jpa.reactive.connection;

import static org.springframework.transaction.reactive.TransactionSynchronizationManager.forCurrentTransaction;

import java.util.Objects;
import org.hibernate.reactive.stage.Stage;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class TransactionUtils {

  private TransactionUtils() {}

  private static final Object KEY = TransactionUtils.class;

  public static Context setTransactionState(Stage.SessionFactory sessionFactory) {
    return Context.of(KEY, isTransactionAvailable(sessionFactory));
  }

  public static Mono<Boolean> isTransactionAvailable(Stage.SessionFactory sessionFactory) {
    return forCurrentTransaction()
        .mapNotNull(tsm -> tsm.getResource(sessionFactory))
        .filter(ConnectionHolder.class::isInstance)
        .onErrorResume(e -> Mono.empty())
        .map(Objects::nonNull)
        .defaultIfEmpty(Boolean.FALSE);
  }
}
