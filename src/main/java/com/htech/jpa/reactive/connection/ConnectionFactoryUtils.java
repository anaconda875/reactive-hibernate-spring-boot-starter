package com.htech.jpa.reactive.connection;

import static org.springframework.data.repository.util.ReactiveWrapperConverters.toWrapper;

import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.mutiny.impl.MutinySessionImpl;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;

public class ConnectionFactoryUtils {

  private ConnectionFactoryUtils() {}

  public static Mono<Void> releaseConnection(
      MutinySessionImpl con, Mutiny.SessionFactory connectionFactory) {
    return doReleaseConnection(con, connectionFactory)
        .onErrorMap(
            ex -> new DataAccessResourceFailureException("Failed to close R2DBC Connection", ex));
  }

  public static Mono<Void> doReleaseConnection(
      MutinySessionImpl connection, Mutiny.SessionFactory connectionFactory) {
    return TransactionSynchronizationManager.forCurrentTransaction()
        .flatMap(
            synchronizationManager -> {
              ConnectionHolder conHolder =
                  (ConnectionHolder) synchronizationManager.getResource(connectionFactory);
              if (conHolder != null && connectionEquals(conHolder, connection)) {
                // It's the transactional Connection: Don't close it.
                conHolder.released();
                return Mono.empty();
              }
              return toWrapper(connection.close(), Mono.class);
            })
        .onErrorResume(
            NoTransactionException.class, ex -> toWrapper(connection.close(), Mono.class));
  }

  private static boolean connectionEquals(
      ConnectionHolder conHolder, MutinySessionImpl passedInCon) {
    if (!conHolder.hasConnection()) {
      return false;
    }
    MutinySessionImpl heldCon = conHolder.getConnection();
    // Explicitly check for identity too: for Connection handles that do not implement
    // "equals" properly).
    return (heldCon == passedInCon
        || heldCon.equals(passedInCon)
        || getTargetConnection(heldCon).equals(passedInCon));
  }

  public static MutinySessionImpl getTargetConnection(MutinySessionImpl con) {
    MutinySessionImpl conToUse = con;
    //    while (conToUse instanceof Wrapped<?>) {
    //      conToUse = ((Wrapped<Connection>) conToUse).unwrap();
    //    }
    return conToUse;
  }
}
