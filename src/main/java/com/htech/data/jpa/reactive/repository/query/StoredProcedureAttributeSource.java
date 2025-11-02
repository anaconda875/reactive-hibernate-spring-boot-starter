package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.NamedStoredProcedureQueries;
import jakarta.persistence.NamedStoredProcedureQuery;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureParameter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.jpa.repository.query.JpaEntityMetadata;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * @author Bao.Ngo
 */
public enum StoredProcedureAttributeSource {

  INSTANCE;

  public StoredProcedureAttributes createFrom(Method method, JpaEntityMetadata<?> entityMetadata) {

    Assert.notNull(method, "Method must not be null");
    Assert.notNull(entityMetadata, "EntityMetadata must not be null");

    Procedure procedure = AnnotatedElementUtils.findMergedAnnotation(method, Procedure.class);
    Assert.notNull(procedure, "Method must have an @Procedure annotation");

    NamedStoredProcedureQuery namedStoredProc =
        tryFindAnnotatedNamedStoredProcedureQuery(method, entityMetadata, procedure);

    if (namedStoredProc != null) {
      return newProcedureAttributesFrom(method, namedStoredProc, procedure);
    }

    String procedureName = deriveProcedureNameFrom(method, procedure);
    if (ObjectUtils.isEmpty(procedureName)) {
      throw new IllegalArgumentException(
          "Could not determine name of procedure for @Procedure annotated method: " + method);
    }

    return new StoredProcedureAttributes(
        procedureName, createOutputProcedureParameterFrom(method, procedure));
  }

  private String deriveProcedureNameFrom(Method method, Procedure procedure) {

    if (StringUtils.hasText(procedure.value())) {
      return procedure.value();
    }

    String procedureName = procedure.procedureName();
    return StringUtils.hasText(procedureName) ? procedureName : method.getName();
  }

  private StoredProcedureAttributes newProcedureAttributesFrom(
      Method method, NamedStoredProcedureQuery namedStoredProc, Procedure procedure) {

    List<ProcedureParameter> outputParameters;

    if (!procedure.outputParameterName().isEmpty()) {

      // we give the output parameter definition from the @Procedure annotation precedence
      outputParameters =
          Collections.singletonList(createOutputProcedureParameterFrom(method, procedure));
    } else {

      // try to discover the output parameter
      outputParameters =
          extractOutputParametersFrom(namedStoredProc).stream() //
              .map(
                  namedParameter ->
                      new ProcedureParameter(
                          namedParameter.name(), namedParameter.mode(), namedParameter.type())) //
              .collect(Collectors.toList());
    }

    return new StoredProcedureAttributes(namedStoredProc.name(), outputParameters, true);
  }

  private ProcedureParameter createOutputProcedureParameterFrom(
      Method method, Procedure procedure) {

    return new ProcedureParameter(
        procedure.outputParameterName(),
        procedure.refCursor() ? ParameterMode.REF_CURSOR : ParameterMode.OUT,
        method.getReturnType());
  }

  private List<StoredProcedureParameter> extractOutputParametersFrom(
      NamedStoredProcedureQuery namedStoredProc) {

    List<StoredProcedureParameter> outputParameters = new ArrayList<>();

    for (StoredProcedureParameter param : namedStoredProc.parameters()) {

      switch (param.mode()) {
        case OUT:
        case INOUT:
        case REF_CURSOR:
          outputParameters.add(param);
          break;
        case IN:
        default:
          continue;
      }
    }

    return outputParameters;
  }

  @Nullable
  private NamedStoredProcedureQuery tryFindAnnotatedNamedStoredProcedureQuery(
      Method method, JpaEntityMetadata<?> entityMetadata, Procedure procedure) {

    Assert.notNull(method, "Method must not be null");
    Assert.notNull(entityMetadata, "EntityMetadata must not be null");
    Assert.notNull(procedure, "Procedure must not be null");

    Class<?> entityType = entityMetadata.getJavaType();

    List<NamedStoredProcedureQuery> queries = collectNamedStoredProcedureQueriesFrom(entityType);

    if (queries.isEmpty()) {
      return null;
    }

    String namedProcedureName = derivedNamedProcedureNameFrom(method, entityMetadata, procedure);

    for (NamedStoredProcedureQuery query : queries) {

      if (query.name().equals(namedProcedureName)) {
        return query;
      }
    }

    return null;
  }

  private String derivedNamedProcedureNameFrom(
      Method method, JpaEntityMetadata<?> entityMetadata, Procedure procedure) {

    return StringUtils.hasText(procedure.name()) //
        ? procedure.name() //
        : entityMetadata.getEntityName() + "." + method.getName();
  }

  private List<NamedStoredProcedureQuery> collectNamedStoredProcedureQueriesFrom(
      Class<?> entityType) {

    List<NamedStoredProcedureQuery> queries = new ArrayList<>();

    NamedStoredProcedureQueries namedQueriesAnnotation =
        AnnotatedElementUtils.findMergedAnnotation(entityType, NamedStoredProcedureQueries.class);
    if (namedQueriesAnnotation != null) {
      queries.addAll(Arrays.asList(namedQueriesAnnotation.value()));
    }

    NamedStoredProcedureQuery namedQueryAnnotation =
        AnnotatedElementUtils.findMergedAnnotation(entityType, NamedStoredProcedureQuery.class);
    if (namedQueryAnnotation != null) {
      queries.add(namedQueryAnnotation);
    }

    return queries;
  }
}
