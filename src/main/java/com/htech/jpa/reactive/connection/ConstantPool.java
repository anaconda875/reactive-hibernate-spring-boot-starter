package com.htech.jpa.reactive.connection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class ConstantPool<T> {

  private final ConcurrentMap<String, T> constants = new ConcurrentHashMap<>();

  @Override
  public String toString() {
    return "ConstantPool{" + "constants=" + this.constants + '}';
  }

  abstract T createConstant(String name, boolean sensitive);

  final T valueOf(String name, boolean sensitive) {
    return this.constants.computeIfAbsent(name, n -> createConstant(n, sensitive));
  }
}
