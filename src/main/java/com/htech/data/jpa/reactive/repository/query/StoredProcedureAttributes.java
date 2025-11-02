package com.htech.data.jpa.reactive.repository.query;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Bao.Ngo
 */
public class StoredProcedureAttributes {

  static final String SYNTHETIC_OUTPUT_PARAMETER_NAME = "out";

  private final boolean namedStoredProcedure;
  private final String procedureName;
  private final List<ProcedureParameter> outputProcedureParameters;

  StoredProcedureAttributes(String procedureName, ProcedureParameter parameter) {
    this(procedureName, Collections.singletonList(parameter), false);
  }

  StoredProcedureAttributes(
      String procedureName,
      List<ProcedureParameter> outputProcedureParameters,
      boolean namedStoredProcedure) {

    Assert.notNull(procedureName, "ProcedureName must not be null");
    Assert.notNull(outputProcedureParameters, "OutputProcedureParameters must not be null");
    Assert.isTrue(
        outputProcedureParameters.size() != 1 || outputProcedureParameters.get(0) != null,
        "ProcedureParameters must not have size 1 with a null value");

    this.procedureName = procedureName;
    this.namedStoredProcedure = namedStoredProcedure;

    if (namedStoredProcedure) {
      this.outputProcedureParameters = outputProcedureParameters;
    } else {
      this.outputProcedureParameters = getParametersWithCompletedNames(outputProcedureParameters);
    }
  }

  private List<ProcedureParameter> getParametersWithCompletedNames(
      List<ProcedureParameter> procedureParameters) {

    return IntStream.range(0, procedureParameters.size()) //
        .mapToObj(i -> getParameterWithCompletedName(procedureParameters.get(i), i)) //
        .collect(Collectors.toList());
  }

  private ProcedureParameter getParameterWithCompletedName(ProcedureParameter parameter, int i) {

    return new ProcedureParameter(
        completeOutputParameterName(i, parameter.getName()),
        parameter.getMode(),
        parameter.getType());
  }

  private String completeOutputParameterName(int i, String paramName) {

    return StringUtils.hasText(paramName) //
        ? paramName //
        : createSyntheticParameterName(i);
  }

  private String createSyntheticParameterName(int i) {
    return SYNTHETIC_OUTPUT_PARAMETER_NAME + (i == 0 ? "" : i);
  }

  public String getProcedureName() {
    return procedureName;
  }

  public boolean isNamedStoredProcedure() {
    return namedStoredProcedure;
  }

  public List<ProcedureParameter> getOutputProcedureParameters() {
    return outputProcedureParameters;
  }

  public boolean hasReturnValue() {

    if (getOutputProcedureParameters().isEmpty()) return false;

    Class<?> outputType = getOutputProcedureParameters().get(0).getType();
    return !(void.class.equals(outputType) || Void.class.equals(outputType));
  }
}
