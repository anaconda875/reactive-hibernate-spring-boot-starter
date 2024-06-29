package com.htech.jpa.reactive.connection;

public class IsolationLevel implements TransactionDefinition {

  private static final ConstantPool<IsolationLevel> CONSTANTS =
      new ConstantPool<IsolationLevel>() {

        @Override
        IsolationLevel createConstant(String name, boolean sensitive) {
          return new IsolationLevel(name);
        }
      };

  public static final IsolationLevel READ_COMMITTED = IsolationLevel.valueOf("READ COMMITTED");

  public static final IsolationLevel READ_UNCOMMITTED = IsolationLevel.valueOf("READ UNCOMMITTED");

  public static final IsolationLevel REPEATABLE_READ = IsolationLevel.valueOf("REPEATABLE READ");

  public static final IsolationLevel SERIALIZABLE = IsolationLevel.valueOf("SERIALIZABLE");

  private final String sql;

  private IsolationLevel(String sql) {
    this.sql = sql;
  }

  public static IsolationLevel valueOf(String sql) {
    return CONSTANTS.valueOf(sql, false);
  }

  @Override
  public <T> T getAttribute(Option<T> option) {
    if (option.equals(TransactionDefinition.ISOLATION_LEVEL)) {
      return option.cast(this);
    }

    return null;
  }

  public String asSql() {
    return this.sql;
  }

  @Override
  public String toString() {
    return "IsolationLevel{" + "sql='" + this.sql + '\'' + '}';
  }
}
