package com.htech.jpa.reactive.connection;

import java.time.Duration;
import org.hibernate.reactive.pool.ReactiveConnection;
import org.hibernate.reactive.stage.Stage;
import org.hibernate.reactive.stage.impl.StageSessionImpl;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.reactive.AbstractReactiveTransactionManager;
import org.springframework.transaction.reactive.GenericReactiveTransaction;
import org.springframework.transaction.reactive.TransactionSynchronizationManager;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

/**
 * @author Bao.Ngo
 */
public class ReactiveHibernateTransactionManager extends AbstractReactiveTransactionManager
    implements InitializingBean {

  @Nullable private Stage.SessionFactory sessionFactory;

  private boolean enforceReadOnly = false;

  public ReactiveHibernateTransactionManager() {}

  public ReactiveHibernateTransactionManager(Stage.SessionFactory sessionFactory) {
    this();
    setSessionFactory(sessionFactory);
    afterPropertiesSet();
  }

  public void setSessionFactory(@Nullable Stage.SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Nullable
  public Stage.SessionFactory getSessionFactory() {
    return this.sessionFactory;
  }

  protected Stage.SessionFactory obtainSessionFactory() {
    Stage.SessionFactory connectionFactory = getSessionFactory();
    Assert.state(connectionFactory != null, "No Stage.SessionFactory set");
    return connectionFactory;
  }

  public void setEnforceReadOnly(boolean enforceReadOnly) {
    this.enforceReadOnly = enforceReadOnly;
  }

  public boolean isEnforceReadOnly() {
    return this.enforceReadOnly;
  }

  @Override
  public void afterPropertiesSet() {
    if (getSessionFactory() == null) {
      throw new IllegalArgumentException("Property 'connectionFactory' is required");
    }
  }

  @Override
  protected Object doGetTransaction(TransactionSynchronizationManager synchronizationManager) {
    ConnectionFactoryTransactionObject txObject = new ConnectionFactoryTransactionObject();
    ConnectionHolder conHolder =
        (ConnectionHolder) synchronizationManager.getResource(obtainSessionFactory());
    txObject.setConnectionHolder(conHolder, false);

    return txObject;
  }

  @Override
  protected boolean isExistingTransaction(Object transaction) {
    return ((ConnectionFactoryTransactionObject) transaction).isTransactionActive();
  }

  @Override
  protected Mono<Void> doBegin(
      TransactionSynchronizationManager synchronizationManager,
      Object transaction,
      TransactionDefinition definition) {

    ConnectionFactoryTransactionObject txObject = (ConnectionFactoryTransactionObject) transaction;

    /*if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED &&
        txObject.isTransactionActive()) {
      return txObject.createSavepoint();
    }*/

    return Mono.defer(
            () -> {
              Mono<StageSessionImpl> connectionMono;

              if (!txObject.hasConnectionHolder()
                  || txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
                Mono<StageSessionImpl> newCon =
                    Mono.defer(
                        () ->
                            Mono.fromCompletionStage(obtainSessionFactory().openSession())
                                .map(StageSessionImpl.class::cast));
                connectionMono =
                    newCon.doOnNext(
                        connection -> {
                          if (logger.isDebugEnabled()) {
                            logger.debug(
                                "Acquired StageSessionImpl ["
                                    + connection
                                    + "] for R2DBC transaction");
                          }
                          txObject.setConnectionHolder(new ConnectionHolder(connection), true);
                        });
              } else {
                txObject.getConnectionHolder().setSynchronizedWithTransaction(true);
                connectionMono = Mono.just(txObject.getConnectionHolder().getConnection());
              }

              return connectionMono
                  .flatMap(
                      con ->
                          doBegin(con, txObject, definition)
                              .then(prepareTransactionalConnection(con, definition))
                              .doOnSuccess(
                                  v -> {
                                    txObject.getConnectionHolder().setTransactionActive(true);
                                    Duration timeout = determineTimeout(definition);
                                    if (!timeout.isNegative() && !timeout.isZero()) {
                                      txObject
                                          .getConnectionHolder()
                                          .setTimeoutInMillis(timeout.toMillis());
                                    }
                                    // Bind the connection holder to the thread.
                                    if (txObject.isNewConnectionHolder()) {
                                      synchronizationManager.bindResource(
                                          obtainSessionFactory(), txObject.getConnectionHolder());
                                    }
                                  })
                              .thenReturn(con)
                              .onErrorResume(
                                  ex -> {
                                    if (txObject.isNewConnectionHolder()) {
                                      return ConnectionFactoryUtils.releaseConnection(
                                              con, obtainSessionFactory())
                                          .doOnTerminate(
                                              () -> txObject.setConnectionHolder(null, false))
                                          .then(Mono.error(ex));
                                    }
                                    return Mono.error(ex);
                                  }))
                  .onErrorMap(
                      ex ->
                          new CannotCreateTransactionException(
                              "Could not open R2DBC StageSessionImpl for transaction", ex));
            })
        .then();
  }

  private Mono<Void> doBegin(
      StageSessionImpl con,
      ConnectionFactoryTransactionObject transaction,
      TransactionDefinition definition) {
    return Mono.defer(
            () -> Mono.fromCompletionStage(con.getReactiveConnection().beginTransaction()))
        .then(
            Mono.defer(
                () -> Mono.fromRunnable(() -> con.setDefaultReadOnly(definition.isReadOnly()))));
  }

  /*  protected io.r2dbc.spi.TransactionDefinition createTransactionDefinition(TransactionDefinition definition) {
    // Apply specific isolation level, if any.
    IsolationLevel isolationLevelToUse = resolveIsolationLevel(definition.getIsolationLevel());
    return new ExtendedTransactionDefinition(definition.getName(), definition.isReadOnly(),
        definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT ? isolationLevelToUse : null,
        determineTimeout(definition));
  }*/

  protected Duration determineTimeout(TransactionDefinition definition) {
    if (definition.getTimeout() != TransactionDefinition.TIMEOUT_DEFAULT) {
      return Duration.ofSeconds(definition.getTimeout());
    }
    return Duration.ZERO;
  }

  @Override
  protected Mono<Object> doSuspend(
      TransactionSynchronizationManager synchronizationManager, Object transaction) {
    return Mono.defer(
        () -> {
          ConnectionFactoryTransactionObject txObject =
              (ConnectionFactoryTransactionObject) transaction;
          txObject.setConnectionHolder(null);
          return Mono.justOrEmpty(synchronizationManager.unbindResource(obtainSessionFactory()));
        });
  }

  @Override
  protected Mono<Void> doResume(
      TransactionSynchronizationManager synchronizationManager,
      @Nullable Object transaction,
      Object suspendedResources) {

    return Mono.defer(
        () -> {
          synchronizationManager.bindResource(obtainSessionFactory(), suspendedResources);
          return Mono.empty();
        });
  }

  @Override
  protected Mono<Void> doCommit(
      TransactionSynchronizationManager TransactionSynchronizationManager,
      GenericReactiveTransaction status) {

    ConnectionFactoryTransactionObject txObject =
        (ConnectionFactoryTransactionObject) status.getTransaction();
    if (status.isDebug()) {
      logger.debug(
          "Committing R2DBC transaction on StageSessionImpl ["
              + txObject.getConnectionHolder().getConnection()
              + "]");
    }
    return txObject
        .commit() /*.onErrorMap(R2dbcException.class, ex -> translateException("R2DBC commit", ex))*/;
  }

  @Override
  protected Mono<Void> doRollback(
      TransactionSynchronizationManager TransactionSynchronizationManager,
      GenericReactiveTransaction status) {

    ConnectionFactoryTransactionObject txObject =
        (ConnectionFactoryTransactionObject) status.getTransaction();
    if (status.isDebug()) {
      logger.debug(
          "Rolling back R2DBC transaction on StageSessionImpl ["
              + txObject.getConnectionHolder().getConnection()
              + "]");
    }
    return txObject
        .rollback() /*.onErrorMap(R2dbcException.class, ex -> translateException("R2DBC rollback", ex))*/;
  }

  @Override
  protected Mono<Void> doSetRollbackOnly(
      TransactionSynchronizationManager synchronizationManager, GenericReactiveTransaction status) {

    return Mono.fromRunnable(
        () -> {
          ConnectionFactoryTransactionObject txObject =
              (ConnectionFactoryTransactionObject) status.getTransaction();
          if (status.isDebug()) {
            logger.debug(
                "Setting R2DBC transaction ["
                    + txObject.getConnectionHolder().getConnection()
                    + "] rollback-only");
          }
          txObject.setRollbackOnly();
        });
  }

  @Override
  protected Mono<Void> doCleanupAfterCompletion(
      TransactionSynchronizationManager synchronizationManager, Object transaction) {

    return Mono.defer(
        () -> {
          ConnectionFactoryTransactionObject txObject =
              (ConnectionFactoryTransactionObject) transaction;

          /*if (txObject.hasSavepoint()) {
            // Just release the savepoint, keeping the transactional connection.
            return txObject.releaseSavepoint();
          }*/

          // Remove the connection holder from the context, if exposed.
          if (txObject.isNewConnectionHolder()) {
            synchronizationManager.unbindResource(obtainSessionFactory());
          }

          // Reset connection.
          try {
            if (txObject.isNewConnectionHolder()) {
              StageSessionImpl con = txObject.getConnectionHolder().getConnection();
              if (logger.isDebugEnabled()) {
                logger.debug("Releasing R2DBC StageSessionImpl [" + con + "] after transaction");
              }
              Mono<Void> restoreMono = Mono.empty();
              /*if (txObject.isMustRestoreAutoCommit() && !con.isAutoCommit()) {
                restoreMono = Mono.from(con.setAutoCommit(true));
                if (logger.isDebugEnabled()) {
                  restoreMono = restoreMono.doOnError(ex ->
                      logger.debug(String.format("Error ignored during auto-commit restore: %s", ex)));
                }
                restoreMono = restoreMono.onErrorComplete();
              }*/
              Mono<Void> releaseMono =
                  ConnectionFactoryUtils.releaseConnection(con, obtainSessionFactory());
              if (logger.isDebugEnabled()) {
                releaseMono =
                    releaseMono.doOnError(
                        ex ->
                            logger.debug(
                                String.format("Error ignored during connection release: %s", ex)));
              }
              releaseMono = releaseMono.onErrorComplete();
              return restoreMono.then(releaseMono);
            }
          } finally {
            txObject.getConnectionHolder().clear();
          }

          return Mono.empty();
        });
  }

  protected Mono<Void> prepareTransactionalConnection(
      StageSessionImpl con, TransactionDefinition definition) {
    Mono<Void> prepare = Mono.empty();
    if (isEnforceReadOnly() && definition.isReadOnly()) {
      prepare =
          Mono.fromCompletionStage(
              con.getReactiveConnection().execute("SET TRANSACTION READ ONLY"));
    }
    return prepare;
  }

  /*@Nullable
  protected IsolationLevel resolveIsolationLevel(int isolationLevel) {
    return switch (isolationLevel) {
      case TransactionDefinition.ISOLATION_READ_COMMITTED -> IsolationLevel.READ_COMMITTED;
      case TransactionDefinition.ISOLATION_READ_UNCOMMITTED -> IsolationLevel.READ_UNCOMMITTED;
      case TransactionDefinition.ISOLATION_REPEATABLE_READ -> IsolationLevel.REPEATABLE_READ;
      case TransactionDefinition.ISOLATION_SERIALIZABLE -> IsolationLevel.SERIALIZABLE;
      default -> null;
    };
  }*/

  /*protected RuntimeException translateException(String task, R2dbcException ex) {
    return ConnectionFactoryUtils.convertR2dbcException(task, null, ex);
  }*/

  private record ExtendedTransactionDefinition(
      @Nullable String transactionName,
      boolean readOnly,
      @Nullable IsolationLevel isolationLevel,
      Duration lockWaitTimeout)
      implements com.htech.jpa.reactive.connection.TransactionDefinition {

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getAttribute(Option<T> option) {
      return (T) doGetValue(option);
    }

    @Nullable
    private Object doGetValue(Option<?> option) {
      if (com.htech.jpa.reactive.connection.TransactionDefinition.ISOLATION_LEVEL.equals(option)) {
        return this.isolationLevel;
      }
      if (com.htech.jpa.reactive.connection.TransactionDefinition.NAME.equals(option)) {
        return this.transactionName;
      }
      if (com.htech.jpa.reactive.connection.TransactionDefinition.READ_ONLY.equals(option)) {
        return this.readOnly;
      }
      if (com.htech.jpa.reactive.connection.TransactionDefinition.LOCK_WAIT_TIMEOUT.equals(option)
          && !this.lockWaitTimeout.isZero()) {
        return this.lockWaitTimeout;
      }
      return null;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder(128);
      sb.append(getClass().getSimpleName());
      sb.append(" [transactionName='").append(this.transactionName).append('\'');
      sb.append(", readOnly=").append(this.readOnly);
      sb.append(", isolationLevel=").append(this.isolationLevel);
      sb.append(", lockWaitTimeout=").append(this.lockWaitTimeout);
      sb.append(']');
      return sb.toString();
    }
  }

  private static class ConnectionFactoryTransactionObject {

    @Nullable private ConnectionHolder connectionHolder;

    private boolean newConnectionHolder;

    private boolean mustRestoreAutoCommit;

    @Nullable private String savepointName;

    void setConnectionHolder(
        @Nullable ConnectionHolder connectionHolder, boolean newConnectionHolder) {
      setConnectionHolder(connectionHolder);
      this.newConnectionHolder = newConnectionHolder;
    }

    boolean isNewConnectionHolder() {
      return this.newConnectionHolder;
    }

    public void setConnectionHolder(@Nullable ConnectionHolder connectionHolder) {
      this.connectionHolder = connectionHolder;
    }

    public ConnectionHolder getConnectionHolder() {
      Assert.state(this.connectionHolder != null, "No ConnectionHolder available");
      return this.connectionHolder;
    }

    public boolean hasConnectionHolder() {
      return (this.connectionHolder != null);
    }

    public void setMustRestoreAutoCommit(boolean mustRestoreAutoCommit) {
      this.mustRestoreAutoCommit = mustRestoreAutoCommit;
    }

    public boolean isMustRestoreAutoCommit() {
      return this.mustRestoreAutoCommit;
    }

    public boolean isTransactionActive() {
      return (this.connectionHolder != null && this.connectionHolder.isTransactionActive());
    }

    /*public boolean hasSavepoint() {
      return (this.savepointName != null);
    }*/

    /*public Mono<Void> createSavepoint() {
      ConnectionHolder holder = getConnectionHolder();
      String currentSavepoint = holder.nextSavepoint();
      this.savepointName = currentSavepoint;
      return Mono.from(holder.getConnection().createSavepoint(currentSavepoint));
    }*/

    /*public Mono<Void> releaseSavepoint() {
      String currentSavepoint = this.savepointName;
      if (currentSavepoint == null) {
        return Mono.empty();
      }
      this.savepointName = null;
      return Mono.from(getConnectionHolder().getConnection().releaseSavepoint(currentSavepoint));
    }*/

    public Mono<Void> commit() {
      return /*(hasSavepoint() ? Mono.empty() :*/ Mono.defer(
              () -> Mono.just(getConnectionHolder().getConnection().getReactiveConnection()))
          .map(ReactiveConnection::commitTransaction)
          .flatMap(Mono::fromCompletionStage)
      /*)*/ ;
    }

    public Mono<Void> rollback() {
      //      String currentSavepoint = this.savepointName;
      return /*(currentSavepoint != null ?
             Mono.from(connection.rollbackTransactionToSavepoint(currentSavepoint)) :*/ Mono
          .defer(() -> Mono.just(getConnectionHolder().getConnection().getReactiveConnection()))
          .map(ReactiveConnection::rollbackTransaction)
          .flatMap(Mono::fromCompletionStage) /*)*/;
    }

    public void setRollbackOnly() {
      getConnectionHolder().setRollbackOnly();
    }
  }
}
