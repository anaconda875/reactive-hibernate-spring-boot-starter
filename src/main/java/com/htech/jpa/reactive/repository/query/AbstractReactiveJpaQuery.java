package com.htech.jpa.reactive.repository.query;

import jakarta.persistence.*;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.util.JpaMetamodel;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.util.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractReactiveJpaQuery implements RepositoryQuery {

//  protected final R2dbcQueryMethod method;
//  protected final R2dbcQueryMethod method;
  protected final ReactiveJpaQueryMethod method;
  protected final Mutiny.SessionFactory sessionFactory;
  protected final JpaMetamodel metamodel;
//  private final PersistenceProvider provider;
  protected final Lazy<ReactiveJpaQueryExecution> execution;

  final Lazy<ParameterBinder> parameterBinder = Lazy.of(this::createBinder);

  public AbstractReactiveJpaQuery(ReactiveJpaQueryMethod method, Mutiny.SessionFactory sessionFactory) {

    Assert.notNull(method, "R2dbcQueryMethod must not be null");
    Assert.notNull(sessionFactory, "EntityManager must not be null");

    this.method = method;
    this.sessionFactory = sessionFactory;
    this.metamodel = JpaMetamodel.of(sessionFactory.getMetamodel());
//    this.provider = PersistenceProvider.fromMetamodel().fr(em);
    this.execution = Lazy.of(() -> {

      /*if (method.isStreamQuery()) {
        return new ReactiveJpaQueryExecution.StreamExecution();
      } else*/ /*if (method.isProcedureQuery()) {
        return new ReactiveJpaQueryExecution.ProcedureExecution(method.isCollectionQuery());
      } else*/ if (method.isCollectionQuery()) {
        return new ReactiveJpaQueryExecution.CollectionExecution();
      } /*else if (method.isSliceQuery()) {
        return new ReactiveJpaQueryExecution.SlicedExecution();
      } else if (method.isPageQuery()) {
        return new ReactiveJpaQueryExecution.PagedExecution();
      }*/ else if (method.isModifyingQuery()) {
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

  

  /**
   * Returns the {@link JpaMetamodel}.
   *
   * @return
   */
  protected JpaMetamodel getMetamodel() {
    return metamodel;
  }

  @Nullable
  @Override
  public Object execute(Object[] parameters) {
    return doExecute(getExecution(), parameters);
  }

  /**
   * @param execution
   * @param values
   * @return
   */
  @Nullable
  private Object doExecute(ReactiveJpaQueryExecution execution, Object[] values) {

    ReactiveJpaParametersParameterAccessor accessor = obtainParameterAccessor(values);
    Object result = execution.execute(this, accessor);

    ResultProcessor withDynamicProjection = method.getResultProcessor().withDynamicProjection(accessor);
    //TODO
    return withDynamicProjection.processResult(result, src -> src);
  }

  private ReactiveJpaParametersParameterAccessor obtainParameterAccessor(Object[] values) {

    //TODO
//    if (method.isNativeQuery() && PersistenceProvider.HIBERNATE.equals(provider)) {
//      return new HibernateJpaParametersParameterAccessor(method.getParameters(), values, em);
//    }

    Mutiny.Session session = (Mutiny.Session) values[values.length - 2];
    Mutiny.Transaction transaction = (Mutiny.Transaction) values[values.length - 1];
    Object[] originalParams = Arrays.copyOfRange(values, 0, values.length - 2);

    return new ReactiveJpaParametersParameterAccessor(method.getParameters(), originalParams, sessionFactory, session, transaction);
  }

  protected ReactiveJpaQueryExecution getExecution() {

    ReactiveJpaQueryExecution execution = this.execution.getNullable();

    if (execution != null) {
      return execution;
    }

    if (method.isModifyingQuery()) {
      return new ReactiveJpaQueryExecution.ModifyingExecution(method, sessionFactory);
    } else {
      return new ReactiveJpaQueryExecution.SingleEntityExecution();
    }
  }

  /**
   * Applies the declared query hints to the given query.
   *
   * @param query
   * @return
   */
  protected <T extends Mutiny.AbstractQuery> T applyHints(T query, ReactiveJpaQueryMethod method) {

    /*List<QueryHint> hints = method.getHints();

    if (!hints.isEmpty()) {
      for (QueryHint hint : hints) {
        applyQueryHint(query, hint);
      }
    }*/

    // Apply any meta-attributes that exist
//    if (method.hasQueryMetaAttributes()) {
      //TODO
//      if (provider.getCommentHintKey() != null) {
//        query.setHint( //
//            provider.getCommentHintKey(), provider.getCommentHintValue(method.getQueryMetaAttributes().getComment()));
//      }
//    }

    return query;
  }

  /**
   * Protected to be able to customize in sub-classes.
   *
   * @param query must not be {@literal null}.
   * @param hint must not be {@literal null}.
   */
  protected <T extends Mutiny.AbstractQuery> void applyQueryHint(T query, QueryHint hint) {

//    Assert.notNull(query, "Mutiny.AbstractQuery must not be null");
//    Assert.notNull(hint, "QueryHint must not be null");
//
//    query.setHint(hint.name(), hint.value());
  }

  /**
   * Applies the {@link LockModeType} provided by the {@link R2dbcQueryMethod} to the given {@link Mutiny.AbstractQuery}.
   *
   * @param query must not be {@literal null}.
   * @param method must not be {@literal null}.
   * @return
   */
  private Mutiny.AbstractQuery applyLockMode(Mutiny.AbstractQuery query, ReactiveJpaQueryMethod method) {
    return query;
//    LockModeType lockModeType = method.getLockModeType();
//    return lockModeType == null ? query : query.setLockMode(lockModeType);
  }

  protected ParameterBinder createBinder() {
    //TODO
    return null;
//    return ParameterBinderFactory.createBinder(getQueryMethod().getParameters());
  }

  protected Mutiny.AbstractQuery createQuery(ReactiveJpaParametersParameterAccessor parameters) {
    return applyLockMode(applyEntityGraphConfiguration(applyHints(doCreateQuery(parameters, method), method), method), method);
  }

  /**
   * Configures the {@link jakarta.persistence.EntityGraph} to use for the given {@link R2dbcQueryMethod} if the
   * {@link EntityGraph} annotation is present.
   *
   * @param query must not be {@literal null}.
   * @param method must not be {@literal null}.
   * @return
   */
  private Mutiny.AbstractQuery applyEntityGraphConfiguration(Mutiny.AbstractQuery query, ReactiveJpaQueryMethod method) {
    /*JpaEntityGraph entityGraph = method.getEntityGraph();

    if (entityGraph != null) {
      QueryHints hints = Jpa21Utils.getFetchGraphHint(sessionFactory, method.getEntityGraph(),
          getQueryMethod().getEntityInformation().getJavaType());

      hints.forEach(query::setHint);
    }*/

    return query;
  }

  protected Mutiny.AbstractQuery createCountQuery(ReactiveJpaParametersParameterAccessor values) {
    Mutiny.AbstractQuery countQuery = doCreateCountQuery(values);

    return countQuery;
    
//    return method.applyHintsToCountQuery() ? applyHints(countQuery, method) : countQuery;
  }

  /**
   * Returns the type to be used when creating the JPA query.
   *
   * @return
   * @since 2.0.5
   */
  @Nullable
  protected Class<?> getTypeToRead(ReturnedType returnedType) {
    //TODO
    return null;
    /*if (PersistenceProvider.ECLIPSELINK.equals(provider)) {
      return null;
    }

    return returnedType.isProjecting() && !getMetamodel().isJpaManaged(returnedType.getReturnedType()) //
        ? Tuple.class //
        : null;*/
  }

  protected abstract Mutiny.AbstractQuery doCreateQuery(ReactiveJpaParametersParameterAccessor accessor, ReactiveJpaQueryMethod method);

  /**
   * Creates a {@link TypedQuery} for counting using the given values.
   *
   * @param accessor must not be {@literal null}.
   * @return
   */
  protected abstract Mutiny.AbstractQuery doCreateCountQuery(ReactiveJpaParametersParameterAccessor accessor);

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

    /**
     * A {@link Map} implementation which delegates all calls to a {@link Tuple}. Depending on the provided
     * {@link Tuple} implementation it might return the same value for various keys of which only one will appear in the
     * key/entry set.
     *
     * @author Jens Schauder
     */
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

      /**
       * If the key is not a {@code String} or not a key of the backing {@link Tuple} this returns {@code false}.
       * Otherwise this returns {@code true} even when the value from the backing {@code Tuple} is {@code null}.
       *
       * @param key the key for which to get the value from the map.
       * @return whether the key is an element of the backing tuple.
       */
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

      /**
       * If the key is not a {@code String} or not a key of the backing {@link Tuple} this returns {@code null}.
       * Otherwise the value from the backing {@code Tuple} is returned, which also might be {@code null}.
       *
       * @param key the key for which to get the value from the map.
       * @return the value of the backing {@link Tuple} for that key or {@code null}.
       */
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

