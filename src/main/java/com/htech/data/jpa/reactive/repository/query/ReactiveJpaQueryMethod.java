package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.query.*;
import org.springframework.data.jpa.repository.query.Meta;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.ParametersSource;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.util.QueryExecutionConverters;
import org.springframework.data.util.Lazy;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;

public class ReactiveJpaQueryMethod extends QueryMethod {

  private static final Set<Class<?>> NATIVE_ARRAY_TYPES;
  private static final StoredProcedureAttributeSource STORED_PROCEDURE_ATTRIBUTE_SOURCE =
      StoredProcedureAttributeSource.INSTANCE;

  static {
    Set<Class<?>> types = new HashSet<>();
    types.add(byte[].class);
    types.add(Byte[].class);
    types.add(char[].class);
    types.add(Character[].class);

    NATIVE_ARRAY_TYPES = Collections.unmodifiableSet(types);
  }

  private final ReactiveJpaQueryExtractor extractor;
  private final Method method;

  private final RepositoryMetadata metadata;
  private final Class<?> returnType;

  private @Nullable StoredProcedureAttributes storedProcedureAttributes;
  private final Lazy<LockModeType> lockModeType;
  private final Lazy<QueryHints> queryHints;
  private final Lazy<JpaEntityGraph> jpaEntityGraph;
  private final Lazy<Modifying> modifying;
  private final Lazy<Boolean> isNativeQuery;
  private final Lazy<Boolean> isCollectionQuery;
  private final Lazy<Boolean> isProcedureQuery;
  private final Lazy<JpaEntityMetadata<?>> entityMetadata;
  private final Map<Class<? extends Annotation>, Optional<Annotation>> annotationCache;

  protected ReactiveJpaQueryMethod(
      Method method,
      RepositoryMetadata metadata,
      ProjectionFactory factory,
      ReactiveJpaQueryExtractor extractor) {
    super(method, metadata, factory);

    Assert.notNull(method, "Method must not be null");
    Assert.notNull(extractor, "Query extractor must not be null");

    this.method = method;
    this.metadata = metadata;
    this.returnType = potentiallyUnwrapReturnTypeFor(metadata, method);
    this.extractor = extractor;
    this.lockModeType =
        Lazy.of(
            () ->
                (LockModeType)
                    Optional.ofNullable(
                            AnnotatedElementUtils.findMergedAnnotation(method, Lock.class)) //
                        .map(AnnotationUtils::getValue) //
                        .orElse(null));

    this.queryHints =
        Lazy.of(() -> AnnotatedElementUtils.findMergedAnnotation(method, QueryHints.class));
    this.modifying =
        Lazy.of(() -> AnnotatedElementUtils.findMergedAnnotation(method, Modifying.class));
    this.jpaEntityGraph =
        Lazy.of(
            () -> {
              EntityGraph entityGraph =
                  AnnotatedElementUtils.findMergedAnnotation(method, EntityGraph.class);

              if (entityGraph == null) {
                return null;
              }

              return new JpaEntityGraph(entityGraph, getNamedQueryName());
            });
    this.isNativeQuery = Lazy.of(() -> getAnnotationValue("nativeQuery", Boolean.class));
    this.isCollectionQuery =
        Lazy.of(
            () ->
                (isCollectionQueryInternal() || super.isCollectionQuery())
                    && !NATIVE_ARRAY_TYPES.contains(this.returnType));
    this.isProcedureQuery =
        Lazy.of(() -> AnnotationUtils.findAnnotation(method, Procedure.class) != null);
    this.entityMetadata = Lazy.of(() -> new DefaultJpaEntityMetadata<>(getDomainClass()));
    this.annotationCache = new ConcurrentReferenceHashMap<>();

    Assert.isTrue(
        !(isModifyingQuery() && getParameters().hasSpecialParameter()),
        String.format("Modifying method must not contain %s", Parameters.TYPES));
    assertParameterNamesInAnnotatedQuery();
  }

  private Boolean isCollectionQueryInternal() {
    TypeInformation<?> returnTypeInformation = metadata.getReturnType(method);
    Class<?> type = returnTypeInformation.getType();

    return !ReactiveJpaQueryExecutionConverters.isSingleValue(type);
  }

  private static Class<?> potentiallyUnwrapReturnTypeFor(
      RepositoryMetadata metadata, Method method) {

    TypeInformation<?> returnType = metadata.getReturnType(method);

    while (QueryExecutionConverters.supports(returnType.getType())
        || QueryExecutionConverters.supportsUnwrapping(returnType.getType())) {
      returnType = returnType.getRequiredComponentType();
    }

    return returnType.getType();
  }

  private void assertParameterNamesInAnnotatedQuery() {
    String annotatedQuery = getAnnotatedQuery();

    if (!DeclaredQuery.of(annotatedQuery, this.isNativeQuery.get()).hasNamedParameter()) {
      return;
    }

    for (Parameter parameter : getParameters()) {
      if (!parameter.isNamedParameter()) {
        continue;
      }

      if (!StringUtils.hasText(annotatedQuery)
          || !annotatedQuery.contains(String.format(":%s", parameter.getName().get()))
              && !annotatedQuery.contains(String.format("#%s", parameter.getName().get()))) {
        throw new IllegalStateException(
            String.format(
                "Using named parameters for method %s but parameter '%s' not found in annotated query '%s'",
                method, parameter.getName(), annotatedQuery));
      }
    }
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public JpaEntityMetadata<?> getEntityInformation() {
    return this.entityMetadata.get();
  }

  @Override
  public boolean isModifyingQuery() {
    return modifying.getNullable() != null;
  }

  @SuppressWarnings("unchecked")
  private <A extends Annotation> Optional<A> doFindAnnotation(Class<A> annotationType) {
    return (Optional<A>)
        this.annotationCache.computeIfAbsent(
            annotationType,
            it -> Optional.ofNullable(AnnotatedElementUtils.findMergedAnnotation(method, it)));
  }

  List<QueryHint> getHints() {
    QueryHints hints = this.queryHints.getNullable();
    if (hints != null) {
      return Arrays.asList(hints.value());
    }

    return Collections.emptyList();
  }

  @Nullable
  LockModeType getLockModeType() {
    return lockModeType.getNullable();
  }

  @Nullable
  JpaEntityGraph getEntityGraph() {
    return jpaEntityGraph.getNullable();
  }

  boolean applyHintsToCountQuery() {
    QueryHints hints = this.queryHints.getNullable();
    return hints != null ? hints.forCounting() : false;
  }

  ReactiveJpaQueryExtractor getQueryExtractor() {
    return extractor;
  }

  Class<?> getReturnType() {
    return returnType;
  }

  public boolean hasQueryMetaAttributes() {
    return getMetaAnnotation() != null;
  }

  @Nullable
  org.springframework.data.jpa.repository.Meta getMetaAnnotation() {
    return doFindAnnotation(org.springframework.data.jpa.repository.Meta.class).orElse(null);
  }

  public Meta getQueryMetaAttributes() {
    org.springframework.data.jpa.repository.Meta meta = getMetaAnnotation();
    if (meta == null) {
      return new Meta();
    }

    Meta metaAttributes = new Meta();

    if (StringUtils.hasText(meta.comment())) {
      metaAttributes.setComment(meta.comment());
    }

    return metaAttributes;
  }

  @Nullable
  public String getAnnotatedQuery() {
    String query = getAnnotationValue("value", String.class);
    return StringUtils.hasText(query) ? query : null;
  }

  boolean hasAnnotatedQueryName() {
    return StringUtils.hasText(getAnnotationValue("name", String.class));
  }

  public String getRequiredAnnotatedQuery() throws IllegalStateException {
    String query = getAnnotatedQuery();

    if (query != null) {
      return query;
    }

    throw new IllegalStateException(
        String.format("No annotated query found for query method %s", getName()));
  }

  @Nullable
  public String getCountQuery() {
    String countQuery = getAnnotationValue("countQuery", String.class);
    return StringUtils.hasText(countQuery) ? countQuery : null;
  }

  @Nullable
  String getCountQueryProjection() {
    String countProjection = getAnnotationValue("countProjection", String.class);
    return StringUtils.hasText(countProjection) ? countProjection : null;
  }

  boolean isNativeQuery() {
    return this.isNativeQuery.get();
  }

  @Override
  public String getNamedQueryName() {
    String annotatedName = getAnnotationValue("name", String.class);
    return StringUtils.hasText(annotatedName) ? annotatedName : super.getNamedQueryName();
  }

  String getNamedCountQueryName() {
    String annotatedName = getAnnotationValue("countName", String.class);
    return StringUtils.hasText(annotatedName) ? annotatedName : getNamedQueryName() + ".count";
  }

  boolean getFlushAutomatically() {
    return getMergedOrDefaultAnnotationValue("flushAutomatically", Modifying.class, Boolean.class);
  }

  boolean getClearAutomatically() {
    return getMergedOrDefaultAnnotationValue("clearAutomatically", Modifying.class, Boolean.class);
  }

  private <T> T getAnnotationValue(String attribute, Class<T> type) {
    return getMergedOrDefaultAnnotationValue(attribute, Query.class, type);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private <T> T getMergedOrDefaultAnnotationValue(
      String attribute, Class annotationType, Class<T> targetType) {

    Annotation annotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType);
    if (annotation == null) {
      return targetType.cast(AnnotationUtils.getDefaultValue(annotationType, attribute));
    }

    return targetType.cast(AnnotationUtils.getValue(annotation, attribute));
  }

  @Override
  protected ReactiveJpaParameters createParameters(ParametersSource parametersSource) {
    return new ReactiveJpaParameters(parametersSource);
  }

  @Override
  public ReactiveJpaParameters getParameters() {
    return (ReactiveJpaParameters) super.getParameters();
  }

  @Override
  public boolean isCollectionQuery() {
    return this.isCollectionQuery.get();
  }

  //  @Override
  //  public boolean isStreamQuery() {
  //    return true;
  //  }

  public boolean isProcedureQuery() {
    return this.isProcedureQuery.get();
  }

  StoredProcedureAttributes getProcedureAttributes() {

    if (storedProcedureAttributes == null) {
      this.storedProcedureAttributes =
          STORED_PROCEDURE_ATTRIBUTE_SOURCE.createFrom(method, getEntityInformation());
    }

    return storedProcedureAttributes;
  }

  public Class<? extends QueryRewriter> getQueryRewriter() {
    return getMergedOrDefaultAnnotationValue("queryRewriter", Query.class, Class.class);
  }
}
