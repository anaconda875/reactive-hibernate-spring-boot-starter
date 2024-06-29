package com.htech.jpa.reactive.connection;

import static org.springframework.transaction.reactive.TransactionSynchronizationManager.forCurrentTransaction;

import org.hibernate.reactive.stage.Stage;
import org.hibernate.reactive.stage.impl.StageSessionImpl;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.NoTransactionException;
import reactor.core.publisher.Mono;

public class ConnectionFactoryUtils {

  private ConnectionFactoryUtils() {}

  public static Mono<Void> releaseConnection(
      StageSessionImpl con, Stage.SessionFactory connectionFactory) {
    return doReleaseConnection(con, connectionFactory)
        .onErrorMap(
            ex -> new DataAccessResourceFailureException("Failed to close R2DBC Connection", ex));
  }

  public static Mono<Void> doReleaseConnection(
      StageSessionImpl connection, Stage.SessionFactory connectionFactory) {
    return forCurrentTransaction()
        .flatMap(
            synchronizationManager -> {
              ConnectionHolder conHolder =
                  (ConnectionHolder) synchronizationManager.getResource(connectionFactory);
              if (conHolder != null && connectionEquals(conHolder, connection)) {
                // It's the transactional Connection: Don't close it.
                conHolder.released();
                return Mono.empty();
              }
              return Mono.defer(() -> Mono.fromCompletionStage(connection.close()));
            })
        .onErrorResume(
            NoTransactionException.class,
            ex -> Mono.defer(() -> Mono.fromCompletionStage(connection.close())));
  }

  private static boolean connectionEquals(
      ConnectionHolder conHolder, StageSessionImpl passedInCon) {
    if (!conHolder.hasConnection()) {
      return false;
    }
    StageSessionImpl heldCon = conHolder.getConnection();
    // Explicitly check for identity too: for Connection handles that do not implement
    // "equals" properly).
    return (heldCon == passedInCon
        || heldCon.equals(passedInCon)
        || getTargetConnection(heldCon).equals(passedInCon));
  }

  public static StageSessionImpl getTargetConnection(StageSessionImpl con) {
    StageSessionImpl conToUse = con;
    //    while (conToUse instanceof Wrapped<?>) {
    //      conToUse = ((Wrapped<Connection>) conToUse).unwrap();
    //    }
    return conToUse;
  }
}
