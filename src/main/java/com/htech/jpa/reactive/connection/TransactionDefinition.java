package com.htech.jpa.reactive.connection;

import java.time.Duration;
import org.springframework.lang.Nullable;

public interface TransactionDefinition {
  Option<IsolationLevel> ISOLATION_LEVEL = Option.valueOf("isolationLevel");

  Option<Boolean> READ_ONLY = Option.valueOf("readOnly");

  Option<String> NAME = Option.valueOf("name");

  Option<Duration> LOCK_WAIT_TIMEOUT = Option.valueOf("lockWaitTimeout");

  @Nullable
  <T> T getAttribute(Option<T> option);
}
