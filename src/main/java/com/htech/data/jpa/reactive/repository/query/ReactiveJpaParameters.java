package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.TemporalType;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.data.jpa.repository.Temporal;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;

public class ReactiveJpaParameters
    extends Parameters<ReactiveJpaParameters, ReactiveJpaParameters.JpaParameter> {

  /**
   * Creates a new {@link ReactiveJpaParameters} instance from the given {@link Method}.
   *
   * @param method must not be {@literal null}.
   */
  public ReactiveJpaParameters(Method method) {
    super(method, null);
  }

  private ReactiveJpaParameters(List<ReactiveJpaParameters.JpaParameter> parameters) {
    super(parameters);
  }

  @Override
  protected ReactiveJpaParameters.JpaParameter createParameter(MethodParameter parameter) {
    return new ReactiveJpaParameters.JpaParameter(parameter);
  }

  @Override
  protected ReactiveJpaParameters createFrom(List<ReactiveJpaParameters.JpaParameter> parameters) {
    return new ReactiveJpaParameters(parameters);
  }

  /**
   * @return {@code true} if the method signature declares Limit or Pageable parameters.
   */
  public boolean hasLimitingParameters() {
    return hasLimitParameter() || hasPageableParameter();
  }

  /**
   * Custom {@link Parameter} implementation adding parameters of type {@link Temporal} to the
   * special ones.
   *
   * @author Thomas Darimont
   * @author Oliver Gierke
   */
  public static class JpaParameter extends Parameter {

    private final @Nullable Temporal annotation;
    private @Nullable TemporalType temporalType;

    /**
     * Creates a new {@link ReactiveJpaParameters.JpaParameter}.
     *
     * @param parameter must not be {@literal null}.
     */
    protected JpaParameter(MethodParameter parameter) {

      super(parameter, TypeInformation.of(Parameter.class));

      this.annotation = parameter.getParameterAnnotation(Temporal.class);
      this.temporalType = null;

      if (!isDateParameter() && hasTemporalParamAnnotation()) {
        throw new IllegalArgumentException(
            Temporal.class.getSimpleName() + " annotation is only allowed on Date parameter");
      }
    }

    @Override
    public boolean isBindable() {
      return super.isBindable() || isTemporalParameter();
    }

    /**
     * @return {@literal true} if this parameter is of type {@link Date} and has an {@link Temporal}
     *     annotation.
     */
    boolean isTemporalParameter() {
      return isDateParameter() && hasTemporalParamAnnotation();
    }

    /**
     * @return the {@link TemporalType} on the {@link Temporal} annotation of the given {@link
     *     Parameter}.
     */
    @Nullable
    TemporalType getTemporalType() {

      if (temporalType == null) {
        this.temporalType = annotation == null ? null : annotation.value();
      }

      return this.temporalType;
    }

    /**
     * @return the required {@link TemporalType} on the {@link Temporal} annotation of the given
     *     {@link Parameter}.
     * @throws IllegalStateException if the parameter does not define a {@link TemporalType}.
     * @since 2.0
     */
    TemporalType getRequiredTemporalType() throws IllegalStateException {

      TemporalType temporalType = getTemporalType();

      if (temporalType != null) {
        return temporalType;
      }

      throw new IllegalStateException(
          String.format("Required temporal type not found for %s", getType()));
    }

    private boolean hasTemporalParamAnnotation() {
      return annotation != null;
    }

    private boolean isDateParameter() {
      return getType().equals(Date.class);
    }
  }
}
