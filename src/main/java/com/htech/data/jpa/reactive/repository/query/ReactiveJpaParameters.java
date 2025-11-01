package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.TemporalType;
import java.util.Date;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.data.jpa.repository.Temporal;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.ParametersSource;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;

public class ReactiveJpaParameters
    extends Parameters<ReactiveJpaParameters, ReactiveJpaParameters.JpaParameter> {

  public ReactiveJpaParameters(ParametersSource parametersSource) {
    super(
        parametersSource,
        methodParameter ->
            new JpaParameter(methodParameter, parametersSource.getDomainTypeInformation()));
  }

  private ReactiveJpaParameters(List<ReactiveJpaParameters.JpaParameter> parameters) {
    super(parameters);
  }

  @Override
  protected ReactiveJpaParameters createFrom(List<ReactiveJpaParameters.JpaParameter> parameters) {
    return new ReactiveJpaParameters(parameters);
  }

  public boolean hasLimitingParameters() {
    return hasLimitParameter() || hasPageableParameter();
  }

  public static class JpaParameter extends Parameter {

    private final @Nullable Temporal annotation;
    private @Nullable TemporalType temporalType;

    protected JpaParameter(MethodParameter parameter, TypeInformation<?> domainType) {
      super(parameter, domainType);
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

    boolean isTemporalParameter() {
      return isDateParameter() && hasTemporalParamAnnotation();
    }

    @Nullable
    TemporalType getTemporalType() {
      if (temporalType == null) {
        this.temporalType = annotation == null ? null : annotation.value();
      }

      return this.temporalType;
    }

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
