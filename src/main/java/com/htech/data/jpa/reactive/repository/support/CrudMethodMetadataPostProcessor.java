package com.htech.data.jpa.reactive.repository.support;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Meta;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.support.CrudMethodMetadata;
import org.springframework.data.jpa.repository.support.MutableQueryHints;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Bao.Ngo
 */
public class CrudMethodMetadataPostProcessor implements RepositoryProxyPostProcessor {

  @Override
  public void postProcess(ProxyFactory factory, RepositoryInformation repositoryInformation) {
    factory.addAdvice(
        new CrudMethodMetadataPostProcessor.CrudMethodMetadataPopulatingMethodInterceptor(
            repositoryInformation));
  }

  static class CrudMethodMetadataPopulatingMethodInterceptor implements MethodInterceptor {

    private static final ConcurrentMap<Method, Mono<CrudMethodMetadata>> METADATA_CACHE =
        new ConcurrentHashMap<>();

    private final Set<Method> implementations = new HashSet<>();

    CrudMethodMetadataPopulatingMethodInterceptor(RepositoryInformation repositoryInformation) {
      ReflectionUtils.doWithMethods(
          repositoryInformation.getRepositoryInterface(),
          implementations::add,
          method -> !repositoryInformation.isQueryMethod(method));
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
      Method method = invocation.getMethod();
      Object proceeded = invocation.proceed();

      if (!implementations.contains(method)) {
        return proceeded;
      }

      Mono<CrudMethodMetadata> methodMetadata = METADATA_CACHE.get(method);

      if (methodMetadata == null) {
        Supplier<CrudMethodMetadata> supplier = () -> new DefaultCrudMethodMetadata(method);
        methodMetadata = Mono.fromSupplier(supplier).cache();
        METADATA_CACHE.putIfAbsent(method, methodMetadata);
      }

      if (proceeded instanceof Mono<?> mono) {
        return mono.contextWrite(CrudMethodMetadataContextHolder.set(methodMetadata));
      }

      if (proceeded instanceof Flux<?> flux) {
        return flux.contextWrite(CrudMethodMetadataContextHolder.set(methodMetadata));
      }

      return proceeded;
    }
  }

  private static class DefaultCrudMethodMetadata implements CrudMethodMetadata {

    private final @Nullable LockModeType lockModeType;
    private final org.springframework.data.jpa.repository.support.QueryHints queryHints;
    private final org.springframework.data.jpa.repository.support.QueryHints queryHintsForCount;
    private final @Nullable String comment;
    private final Optional<EntityGraph> entityGraph;
    private final Method method;

    DefaultCrudMethodMetadata(Method method) {
      Assert.notNull(method, "Method must not be null");

      this.lockModeType = findLockModeType(method);
      this.queryHints = findQueryHints(method, it -> true);
      this.queryHintsForCount = findQueryHints(method, QueryHints::forCounting);
      this.comment = findComment(method);
      this.entityGraph = findEntityGraph(method);
      this.method = method;
    }

    private static Optional<EntityGraph> findEntityGraph(Method method) {
      return Optional.ofNullable(
          AnnotatedElementUtils.findMergedAnnotation(method, EntityGraph.class));
    }

    @Nullable
    private static LockModeType findLockModeType(Method method) {
      Lock annotation = AnnotatedElementUtils.findMergedAnnotation(method, Lock.class);
      return annotation == null ? null : (LockModeType) AnnotationUtils.getValue(annotation);
    }

    private static org.springframework.data.jpa.repository.support.QueryHints findQueryHints(
        Method method, Predicate<QueryHints> annotationFilter) {
      MutableQueryHints queryHints = new MutableQueryHints();
      QueryHints queryHintsAnnotation =
          AnnotatedElementUtils.findMergedAnnotation(method, QueryHints.class);
      if (queryHintsAnnotation != null && annotationFilter.test(queryHintsAnnotation)) {
        for (QueryHint hint : queryHintsAnnotation.value()) {
          queryHints.add(hint.name(), hint.value());
        }
      }

      QueryHint queryHintAnnotation = AnnotationUtils.findAnnotation(method, QueryHint.class);
      if (queryHintAnnotation != null) {
        queryHints.add(queryHintAnnotation.name(), queryHintAnnotation.value());
      }

      return queryHints;
    }

    @Nullable
    private static String findComment(Method method) {
      Meta annotation = AnnotatedElementUtils.findMergedAnnotation(method, Meta.class);
      return annotation == null ? null : (String) AnnotationUtils.getValue(annotation, "comment");
    }

    @Nullable
    @Override
    public LockModeType getLockModeType() {
      return lockModeType;
    }

    @Override
    public org.springframework.data.jpa.repository.support.QueryHints getQueryHints() {
      return queryHints;
    }

    @Override
    public org.springframework.data.jpa.repository.support.QueryHints getQueryHintsForCount() {
      return queryHintsForCount;
    }

    @Override
    public String getComment() {
      return comment;
    }

    @Override
    public Optional<EntityGraph> getEntityGraph() {
      return entityGraph;
    }

    @Override
    public Method getMethod() {
      return method;
    }
  }
}
