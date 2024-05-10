package com.htech.jpa.reactive.connection;

import java.util.Objects;
import org.springframework.lang.Nullable;

public class Option<T> {

  private static final ConstantPool<Option<?>> CONSTANTS =
      new ConstantPool<Option<?>>() {

        @Override
        Option<?> createConstant(String name, boolean sensitive) {
          return new Option<>(name, sensitive);
        }
      };

  private final String name;

  private final boolean sensitive;

  private Option(String name, boolean sensitive) {
    this.name = name;
    this.sensitive = sensitive;
  }

  @SuppressWarnings("unchecked")
  public static <T> Option<T> sensitiveValueOf(String name) {
    return (Option<T>) CONSTANTS.valueOf(name, true);
  }

  @SuppressWarnings("unchecked")
  public static <T> Option<T> valueOf(String name) {
    return (Option<T>) CONSTANTS.valueOf(name, false);
  }

  @Nullable
  @SuppressWarnings("unchecked")
  public T cast(@Nullable Object obj) {
    if (obj == null) {
      return null;
    }

    return (T) obj;
  }

  public String name() {
    return this.name;
  }

  @Override
  public String toString() {
    return "Option{" + "name='" + this.name + '\'' + ", sensitive=" + this.sensitive + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Option<?> option = (Option<?>) o;
    return this.sensitive == option.sensitive && this.name.equals(option.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.name, this.sensitive);
  }

  boolean sensitive() {
    return this.sensitive;
  }
}
