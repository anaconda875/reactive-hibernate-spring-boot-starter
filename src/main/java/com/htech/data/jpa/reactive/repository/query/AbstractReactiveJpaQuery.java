package com.htech.data.jpa.reactive.repository.query;

import com.htech.jpa.reactive.connection.SessionContextHolder;
import jakarta.persistence.*;
import java.util.*;
import java.util.stream.Collectors;
import org.hibernate.reactive.stage.Stage;
import org.reactivestreams.Publisher;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.jpa.repository.query.JpaEntityGraph;
import org.springframework.data.jpa.repository.support.QueryHints;
import org.springframework.data.jpa.util.JpaMetamodel;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.util.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

/**
 * @author Bao.Ngo
 */
public abstract class AbstractReactiveJpaQuery implements RepositoryQuery {

  protected final ReactiveJpaQueryMethod method;
  protected final Stage.SessionFactory sessionFactory;
  protected final JpaMetamodel metamodel;
  protected final Lazy<ReactiveJpaQueryExecution> execution;

  final Lazy<ParameterBinder> parameterBinder = Lazy.of(this::createBinder);

  public AbstractReactiveJpaQuery(
      ReactiveJpaQueryMethod method, Stage.SessionFactory sessionFactory) {
    Assert.notNull(method, "R2dbcQueryMethod must not be null");
    Assert.notNull(sessionFactory, "EntityManager must not be null");

    this.method = method;
    this.sessionFactory = sessionFactory;
    this.metamodel = JpaMetamodel.of(sessionFactory.getMetamodel());
    this.execution =
        Lazy.of(
            () -> {

              /*if (method.isStreamQuery()) {
                return new ReactiveJpaQueryExecution.StreamExecution();
              } else*/
              /*if (method.isProcedureQuery()) {
                return new ReactiveJpaQueryExecution.ProcedureExecution(method.isCollectionQuery());
              } else*/ if (method.isCollectionQuery()) {
                return new ReactiveJpaQueryExecution.CollectionExecution();
              } else if (method.isPageQuery()) {
                return new ReactiveJpaQueryExecution.PagedExecution();
              } else if (method.isModifyingQuery()) {
                return null;
              } else {
                return new ReactiveJpaQueryExecution.SingleEntityExecution();
              }
            });
  }

  @Override
  public ReactiveJpaQueryMethod getQueryMethod() {
    return method;
  }

  protected JpaMetamodel getMetamodel() {
    return metamodel;
  }

  @Nullable
  @Override
  public Publisher<?> execute(Object[] parameters) {
    return doExecute(getExecution(), parameters);
  }

  @Nullable
  private Publisher<?> doExecute(ReactiveJpaQueryExecution execution, Object[] parameters) {
    Mono<Tuple2<ReactiveJpaParametersParameterAccessor, ResultProcessor>> tuple2 =
        obtainParameterAccessor(parameters)
            .zipWhen(
                a -> Mono.fromSupplier(() -> method.getResultProcessor().withDynamicProjection(a)))
            .cache();
    return tuple2.flatMapMany(
        tuple -> {
          ResultProcessor withDynamicProjection = tuple.getT2();
          ReactiveJpaParametersParameterAccessor accessor = tuple.getT1();
          // TODO
          return withDynamicProjection.processResult(
              execution.execute(this, accessor, SessionContextHolder.currentSession()));
        });
    //    ReactiveJpaParametersParameterAccessor accessor = obtainParameterAccessor(parameters);
    //    Publisher<?> result = execution.execute(this, accessor, session);

    //    ResultProcessor withDynamicProjection =
    //        method.getResultProcessor().withDynamicProjection(accessor);
    // TODO
    //    return withDynamicProjection.processResult(result, src -> src);
  }

  private Mono<ReactiveJpaParametersParameterAccessor> obtainParameterAccessor(
      Object[] parameters) {

    // TODO
    //    if (method.isNativeQuery() && PersistenceProvider.HIBERNATE.equals(provider)) {
    //      return new HibernateJpaParametersParameterAccessor(method.getParameters(), parameters,
    // em);
    //    }

    return new ReactiveJpaParametersParameterAccessor(
            method, method.getParameters(), parameters, sessionFactory)
        .resolveParameters();
  }

  protected ReactiveJpaQueryExecution getExecution() {
    ReactiveJpaQueryExecution execution = this.execution.getNullable();

    if (execution != null) {
      return execution;
    }

    if (method.isModifyingQuery()) {
      return new ReactiveJpaQueryExecution.ModifyingExecution(/*method, sessionFactory*/ );
    } else {
      return new ReactiveJpaQueryExecution.SingleEntityExecution();
    }
  }

  protected <T extends Stage.AbstractQuery> T applyHints(T query, ReactiveJpaQueryMethod method) {
    // No hints supported yet
    return query;
  }

  protected <T extends Stage.AbstractQuery> void applyQueryHint(T query, QueryHint hint) {
    // TODO: no hints supported yet
    //    Assert.notNull(query, "Stage.AbstractQuery must not be null");
    //    Assert.notNull(hint, "QueryHint must not be null");
    //
    //    query.setHint(hint.name(), hint.value());
  }

  private Stage.AbstractQuery applyLockMode(
      Stage.AbstractQuery query, ReactiveJpaQueryMethod method) {
    LockModeType lockModeType = method.getLockModeType();
    return doApplyLockMode(query, lockModeType);
  }

  private static Stage.AbstractQuery doApplyLockMode(
      Stage.AbstractQuery query, LockModeType lockModeType) {
    if (lockModeType == null) {
      return query;
    }

    if (query instanceof Stage.SelectionQuery<?> sq) {
      return sq.setLockMode(lockModeType);
    }

    return query;
  }

  protected ParameterBinder createBinder() {
    return ParameterBinderFactory.createBinder(getQueryMethod().getParameters());
  }

  protected Mono<Stage.AbstractQuery> createQuery(
      Mono<Stage.Session> session, ReactiveJpaParametersParameterAccessor parameters) {
    return createQueryKeepSession(session, parameters, method)
        .map(
            t ->
                applyLockMode(
                    applyEntityGraphConfiguration(applyHints(t.getT2(), method), t.getT1(), method),
                    method));
  }

  private Stage.AbstractQuery applyEntityGraphConfiguration(
      Stage.AbstractQuery query, Stage.Session session, ReactiveJpaQueryMethod method) {
    JpaEntityGraph entityGraph = method.getEntityGraph();

    if (entityGraph != null) {
      QueryHints hintsForEntityGraphs =
          Jpa21Utils.getFetchGraphHint(
              session,
              method.getEntityGraph(),
              getQueryMethod().getEntityInformation().getJavaType());
      hintsForEntityGraphs.forEach(
          (k, v) -> doApplyEntityGraphConfiguration(query, (EntityGraph) v));
    }

    return query;
  }

  private static Stage.AbstractQuery doApplyEntityGraphConfiguration(
      Stage.AbstractQuery query, EntityGraph entityGraph) {
    if (entityGraph == null) {
      return query;
    }

    if (query instanceof Stage.SelectionQuery<?> sq) {
      return sq.setPlan(entityGraph);
    }

    return query;
  }

  // may be used for PagedExecution
  protected Mono<Stage.AbstractQuery> createCountQuery(
      Mono<Stage.Session> session, ReactiveJpaParametersParameterAccessor values) {
    //    Stage.AbstractQuery countQuery = doCreateCountQuery(values);

    return doCreateCountQuery(session, values);
    //    return method.applyHintsToCountQuery() ? applyHints(countQuery, method) : countQuery;
  }

  protected Optional<Class<?>> getTypeToRead(ReturnedType returnedType) {
    // TODO
    return Optional.empty();
    /*if (PersistenceProvider.ECLIPSELINK.equals(provider)) {
      return null;
    }

    return returnedType.isProjecting() && !getMetamodel().isJpaManaged(returnedType.getReturnedType()) //
        ? Tuple.class //
        : null;*/
  }

  protected Mono<Tuple2<Stage.Session, Stage.AbstractQuery>> createQueryKeepSession(
      Mono<Stage.Session> session,
      ReactiveJpaParametersParameterAccessor accessor,
      ReactiveJpaQueryMethod method) {
    return session.zipWhen(s -> doCreateQuery(Mono.just(s), accessor, method));
  }

  protected abstract Mono<Stage.AbstractQuery> doCreateQuery(
      Mono<Stage.Session> session,
      ReactiveJpaParametersParameterAccessor accessor,
      ReactiveJpaQueryMethod method);

  protected abstract Mono<Stage.AbstractQuery> doCreateCountQuery(
      Mono<Stage.Session> session, ReactiveJpaParametersParameterAccessor accessor);

  static class TupleConverter implements Converter<Object, Object> {

    private final ReturnedType type;

    public TupleConverter(ReturnedType type) {

      Assert.notNull(type, "Returned type must not be null");

      this.type = type;
    }

    @Override
    public Object convert(Object source) {

      if (!(source instanceof Tuple tuple)) {
        return source;
      }

      List<TupleElement<?>> elements = tuple.getElements();

      if (elements.size() == 1) {

        Object value = tuple.get(elements.get(0));

        if (type.getDomainType().isInstance(value) || type.isInstance(value) || value == null) {
          return value;
        }
      }

      return new TupleConverter.TupleBackedMap(tuple);
    }

    private static class TupleBackedMap implements Map<String, Object> {

      private static final String UNMODIFIABLE_MESSAGE = "A TupleBackedMap cannot be modified";

      private final Tuple tuple;

      TupleBackedMap(Tuple tuple) {
        this.tuple = tuple;
      }

      @Override
      public int size() {
        return tuple.getElements().size();
      }

      @Override
      public boolean isEmpty() {
        return tuple.getElements().isEmpty();
      }

      @Override
      public boolean containsKey(Object key) {

        try {
          tuple.get((String) key);
          return true;
        } catch (IllegalArgumentException e) {
          return false;
        }
      }

      @Override
      public boolean containsValue(Object value) {
        return Arrays.asList(tuple.toArray()).contains(value);
      }

      @Override
      @Nullable
      public Object get(Object key) {

        if (!(key instanceof String)) {
          return null;
        }

        try {
          return tuple.get((String) key);
        } catch (IllegalArgumentException e) {
          return null;
        }
      }

      @Override
      public Object put(String key, Object value) {
        throw new UnsupportedOperationException(UNMODIFIABLE_MESSAGE);
      }

      @Override
      public Object remove(Object key) {
        throw new UnsupportedOperationException(UNMODIFIABLE_MESSAGE);
      }

      @Override
      public void putAll(Map<? extends String, ?> m) {
        throw new UnsupportedOperationException(UNMODIFIABLE_MESSAGE);
      }

      @Override
      public void clear() {
        throw new UnsupportedOperationException(UNMODIFIABLE_MESSAGE);
      }

      @Override
      public Set<String> keySet() {

        return tuple.getElements().stream() //
            .map(TupleElement::getAlias) //
            .collect(Collectors.toSet());
      }

      @Override
      public Collection<Object> values() {
        return Arrays.asList(tuple.toArray());
      }

      @Override
      public Set<Entry<String, Object>> entrySet() {

        return tuple.getElements().stream() //
            .map(e -> new HashMap.SimpleEntry<String, Object>(e.getAlias(), tuple.get(e))) //
            .collect(Collectors.toSet());
      }
    }
  }
}
