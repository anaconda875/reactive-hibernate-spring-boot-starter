package com.htech.jpa.reactive.connection;

import org.hibernate.reactive.mutiny.impl.MutinySessionImpl;
import org.springframework.lang.Nullable;
import org.springframework.transaction.support.ResourceHolderSupport;
import org.springframework.util.Assert;

public class ConnectionHolder extends ResourceHolderSupport {

  static final String SAVEPOINT_NAME_PREFIX = "SAVEPOINT_";

  @Nullable private MutinySessionImpl currentConnection;

  private boolean transactionActive;

  private int savepointCounter = 0;

  public ConnectionHolder(MutinySessionImpl connection) {
    this(connection, false);
  }

  public ConnectionHolder(MutinySessionImpl connection, boolean transactionActive) {
    this.currentConnection = connection;
    this.transactionActive = transactionActive;
  }

  protected boolean hasConnection() {
    return (this.currentConnection != null);
  }

  protected void setTransactionActive(boolean transactionActive) {
    this.transactionActive = transactionActive;
  }

  protected boolean isTransactionActive() {
    return this.transactionActive;
  }

  protected void setConnection(@Nullable MutinySessionImpl connection) {
    this.currentConnection = connection;
  }

  public MutinySessionImpl getConnection() {
    Assert.state(this.currentConnection != null, "Active ReactiveConnection is required");
    return this.currentConnection;
  }

  String nextSavepoint() {
    this.savepointCounter++;
    return SAVEPOINT_NAME_PREFIX + this.savepointCounter;
  }

  @Override
  public void released() {
    super.released();
    if (!isOpen() && this.currentConnection != null) {
      this.currentConnection = null;
    }
  }

  @Override
  public void clear() {
    super.clear();
    this.transactionActive = false;
  }
}
