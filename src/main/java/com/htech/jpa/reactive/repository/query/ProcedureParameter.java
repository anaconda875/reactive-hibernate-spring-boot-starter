package com.htech.jpa.reactive.repository.query;

import jakarta.persistence.ParameterMode;
import org.springframework.lang.Nullable;

import java.util.Objects;

public class ProcedureParameter {

  private final String name;
  private final ParameterMode mode;
  private final Class<?> type;

  ProcedureParameter(@Nullable String name, ParameterMode mode, Class<?> type) {

    this.name = name;
    this.mode = mode;
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public ParameterMode getMode() {
    return mode;
  }

  public Class<?> getType() {
    return type;
  }

  @Override
  public boolean equals(Object o) {

    if (this == o) {
      return true;
    }

    if (!(o instanceof ProcedureParameter)) {
      return false;
    }

    ProcedureParameter that = (ProcedureParameter) o;
    return Objects.equals(name, that.name) && mode == that.mode && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, mode, type);
  }

  @Override
  public String toString() {
    return "ProcedureParameter{" + "name='" + name + '\'' + ", mode=" + mode + ", type=" + type + '}';
  }
}
