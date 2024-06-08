package com.htech.data.jpa.reactive.repository.support;

import static org.springframework.data.jpa.repository.query.QueryUtils.*;

import com.htech.data.jpa.reactive.core.StageReactiveJpaEntityOperations;
import com.htech.data.jpa.reactive.repository.query.QueryUtils;
import com.htech.jpa.reactive.connection.SessionContextHolder;
import jakarta.persistence.criteria.*;
import java.io.Serial;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.stream.StreamSupport;
import org.apache.commons.collections4.IterableUtils;
import org.hibernate.reactive.stage.Stage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.support.PageableUtils;
import org.springframework.data.util.ProxyUtils;
import org.springframework.data.util.Streamable;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
public class SimpleReactiveJpaRepository<T, ID>
    implements ReactiveJpaRepositoryImplementation<T, ID> {

  private final JpaEntityInformation<T, ?> entityInformation;
  private final Stage.SessionFactory sessionFactory;
  private final StageReactiveJpaEntityOperations entityOperations;

  public SimpleReactiveJpaRepository(
      JpaEntityInformation<T, ?> entityInformation,
      Stage.SessionFactory sessionFactory,
      StageReactiveJpaEntityOperations entityOperations) {
    this.entityInformation = entityInformation;
    this.sessionFactory = sessionFactory;
    this.entityOperations = entityOperations;
  }

  //  public static <T, ID> ReactiveJpaRepositoryImplementation<T, ID> createInstance(
  //      JpaEntityInformation<T, ?> entityInformation,
  //      Stage.SessionFactory sessionFactory,
  //      ClassLoader classLoader) {
  //    SimpleReactiveJpaRepository<T, ID> instance = new SimpleReactiveJpaRepository<>();
  //    //        new InternalRepository<>(entityInformation, sessionFactory /*, classLoader*/);
  //    //    ProxyFactory proxyFactory = new ProxyFactory(instance);
  //
  //    //    return (ReactiveJpaRepositoryImplementation<T, ID>)
  // proxyFactory.getProxy(classLoader);
  //    return instance;
  //  }

  @Override
  public <S extends T> Flux<S> findAll() {
    return SessionContextHolder.currentSession()
        .flatMap(
            session ->
                Mono.defer(
                    () -> {
                      CompletionStage<List<T>> resultList =
                          getQuery(session, null, Sort.unsorted()).getResultList();
                      return Mono.fromCompletionStage(resultList);
                    }))
        .flatMapMany(Flux::fromIterable)
        .map(e -> (S) e);
  }

  @Override
  public <S extends T> Mono<S> findById(ID id) {
    return SessionContextHolder.currentSession()
        .flatMap(
            session ->
                Mono.defer(
                        () ->
                            Mono.fromCompletionStage(
                                session.find(entityInformation.getJavaType(), id)))
                    .map(e -> (S) e));
  }

  @Override
  public Mono<T> getReferenceById(ID id) {
    return SessionContextHolder.currentSession()
        .map(session -> session.getReference(entityInformation.getJavaType(), id));
  }

  @Override
  public <S extends T> Mono<S> save(S entity) {
    return entityOperations.persist(entity);
    //    return SessionContextHolder.currentSession()
    //        .flatMap(
    //            session ->
    //                Mono.defer(
    //                    () ->
    //                        Mono.fromCompletionStage(session.persist(entity))
    //                            .then(deferFlushing(session))
    //                            .thenReturn(entity)));
  }

  @Override
  public <S extends T> Flux<S> saveAll(Iterable<S> entities) {
    return entityOperations.persist(entities);
    //    if (IterableUtils.isEmpty(entities)) {
    //      return Flux.empty();
    //    }
    //
    //    return SessionContextHolder.currentSession()
    //        .flatMap(
    //            session ->
    //                Mono.defer(
    //                    () ->
    //                        Mono.fromCompletionStage(
    //                                session.persist(IterableUtils.toList(entities).toArray()))
    //                            .then(deferFlushing(session))
    //                            .then(Mono.just(entities))))
    //        .flatMapMany(Flux::fromIterable);
  }

  @Override
  public Mono<Boolean> existsById(ID id) {
    if (entityInformation.getIdAttribute() == null) {
      return findById(id).map(__ -> Boolean.TRUE).defaultIfEmpty(Boolean.FALSE);
    }

    Iterable<String> idAttributeNames = entityInformation.getIdAttributeNames();
    String entityName = entityInformation.getEntityName();

    return Mono.zip(
            SessionContextHolder.currentSession(),
            Mono.fromSupplier(
                () -> QueryUtils.getExistsQueryString(entityName, "*", idAttributeNames)))
        .flatMap(
            tuple -> {
              Stage.Session session = tuple.getT1();
              String existsQuery = tuple.getT2();
              Stage.SelectionQuery<Long> query = session.createQuery(existsQuery, Long.class);
              if (!entityInformation.hasCompositeId()) {
                return Mono.defer(
                    () -> {
                      query.setParameter(idAttributeNames.iterator().next(), id);
                      return Mono.fromCompletionStage(query.getSingleResult())
                          .defaultIfEmpty(0L)
                          .map(l -> l.equals(1L));
                    });
              }

              for (String idAttributeName : idAttributeNames) {
                Object idAttributeValue =
                    entityInformation.getCompositeIdAttributeValue(id, idAttributeName);

                // TODO
                boolean complexIdParameterValueDiscovered = true /*idAttributeValue != null
            && !query.getParameter(idAttributeName).getParameterType().isAssignableFrom(idAttributeValue.getClass())*/;

                if (complexIdParameterValueDiscovered) {
                  // fall-back to findById(id) which does the proper mapping for the parameter.
                  return findById(id).map(__ -> Boolean.TRUE).defaultIfEmpty(Boolean.FALSE);
                }

                query.setParameter(idAttributeName, idAttributeValue);
              }

              return Mono.fromCompletionStage(query.getSingleResult())
                  .defaultIfEmpty(0L)
                  .map(l -> l.equals(1L));
            });
  }

  @Override
  public <S extends T> Flux<S> findAllById(Iterable<ID> ids) {
    if (IterableUtils.isEmpty(ids)) {
      return Flux.empty();
    }

    // IllegalStateException: Illegal pop() with non-matching JdbcValuesSourceProcessingState
    if (entityInformation.hasCompositeId()) {
      return Flux.concat(
              StreamSupport.stream(ids.spliterator(), false).map(this::findById).toList())
          .map(e -> (S) e);
    }

    return SessionContextHolder.currentSession()
        .flatMap(
            session ->
                Mono.defer(
                    () -> {
                      Collection<ID> idCollection = Streamable.of(ids).toList();
                      ByIdsSpecification<T> specification =
                          new ByIdsSpecification<>(entityInformation);
                      Stage.SelectionQuery<T> query =
                          getQuery(session, specification, Sort.unsorted());

                      return Mono.fromCompletionStage(
                          query
                              .setParameter(specification.parameter, idCollection)
                              .getResultList());
                    }))
        .flatMapMany(Flux::fromIterable)
        .map(e -> (S) e);
  }

  @Override
  public Mono<Long> count() {
    return SessionContextHolder.currentSession()
        .flatMap(
            session ->
                Mono.defer(
                    () -> {
                      Stage.SelectionQuery<Long> query =
                          session.createQuery(getCountQueryString(), Long.class);
                      return Mono.fromCompletionStage(query.getSingleResult());
                    }));
  }

  @Override
  public Mono<Void> deleteById(ID id) {
    return findById(id).flatMap(this::delete);
  }

  @Override
  public Mono<Void> delete(T entity) {
    if (entityInformation.isNew(entity)) {
      return Mono.empty();
    }

    return SessionContextHolder.currentSession()
        .flatMap(
            session ->
                Mono.defer(
                    () -> {
                      Class<?> type = ProxyUtils.getUserClass(entity);
                      return Mono.fromCompletionStage(
                              session.find(type, entityInformation.getId(entity)))
                          .flatMap(
                              e -> {
                                if (session.contains(e)) {
                                  return deferRemoving(session, e).then(deferFlushing(session));
                                }
                                return Mono.defer(
                                    () ->
                                        Mono.fromCompletionStage(session.merge(e))
                                            .flatMap(
                                                r ->
                                                    deferRemoving(session, r)
                                                        .then(deferFlushing(session))));
                              });
                    }));
  }

  @Override
  public Mono<Void> deleteAllById(Iterable<? extends ID> ids) {
    if (IterableUtils.isEmpty(ids)) {
      return Mono.empty();
    }

    return Flux.concat(
            StreamSupport.stream(ids.spliterator(), false).map(this::deleteById).toList())
        .then();
  }

  @Override
  public Mono<Void> deleteAll(Iterable<? extends T> entities) {
    if (IterableUtils.isEmpty(entities)) {
      return Mono.empty();
    }

    return Flux.concat(
            StreamSupport.stream(entities.spliterator(), false).map(this::delete).toList())
        .then();
  }

  @Override
  public Mono<Void> deleteAll() {
    return findAll().concatMap(this::delete).then();
  }

  @Override
  public Flux<T> findAll(Sort sort) {
    return SessionContextHolder.currentSession()
        .flatMap(
            session ->
                Mono.defer(
                    () -> {
                      Stage.SelectionQuery<T> query = getQuery(session, null, sort);
                      return Mono.fromCompletionStage(query.getResultList());
                    }))
        .flatMapMany(Flux::fromIterable);
  }

  @Override
  public Flux<T> findAll(Pageable pageable) {
    if (pageable.isUnpaged()) {
      return findAll();
    }

    return findAll(SessionContextHolder.currentSession(), null, pageable);
  }

  public Flux<T> findAll(Mono<Stage.Session> session, Specification<T> spec, Pageable pageable) {
    return session
        .flatMap(
            s ->
                Mono.defer(
                    () -> {
                      Stage.SelectionQuery<T> query = getQuery(s, spec, pageable);
                      return pageable.isUnpaged()
                          ? Mono.fromCompletionStage(query.getResultList())
                          : readPage(query, entityInformation.getJavaType(), pageable, spec);
                    }))
        .flatMapMany(Flux::fromIterable);
  }

  private static Mono<Void> deferRemoving(Stage.Session session, Object e) {
    return Mono.defer(() -> Mono.fromCompletionStage(session.remove(e)));
  }

  private static Mono<Void> deferFlushing(Stage.Session session) {
    return Mono.defer(() -> Mono.fromCompletionStage(session.flush()));
  }

  private Mono<List<T>> readPage(
      Stage.SelectionQuery<T> query, Class<T> javaType, Pageable pageable, Specification<T> spec) {
    if (pageable.isPaged()) {
      query.setFirstResult(PageableUtils.getOffsetAsInteger(pageable));
      query.setMaxResults(pageable.getPageSize());
    }

    return Mono.defer(() -> Mono.fromCompletionStage(query.getResultList()));
  }

  protected Stage.SelectionQuery<T> getQuery(
      Stage.Session session, @Nullable Specification<T> spec, Pageable pageable) {
    Sort sort = pageable.isPaged() ? pageable.getSort() : Sort.unsorted();
    return getQuery(session, spec, entityInformation.getJavaType(), sort);
  }

  private String getCountQueryString() {
    String countQuery = String.format(COUNT_QUERY_STRING, "*", "%s");
    return getQueryString(countQuery, entityInformation.getEntityName());
  }

  protected Stage.SelectionQuery<T> getQuery(
      Stage.Session session, @Nullable Specification<T> spec, Sort sort) {
    return getQuery(session, spec, entityInformation.getJavaType(), sort);
  }

  protected <S extends T> Stage.SelectionQuery<S> getQuery(
      Stage.Session session, Specification<S> spec, Class<S> domainClass, Sort sort) {
    CriteriaBuilder builder = sessionFactory.getCriteriaBuilder();
    CriteriaQuery<S> query = builder.createQuery(domainClass);

    Root<S> root = applySpecificationToCriteria(spec, domainClass, query);
    query.select(root);

    if (sort.isSorted()) {
      query.orderBy(toOrders(sort, root, builder));
    }

    return applyRepositoryMethodMetadata(session.createQuery(query));
  }

  private <S> Stage.SelectionQuery<S> applyRepositoryMethodMetadata(Stage.SelectionQuery<S> query) {
    // TODO
    return query;
    /*if (metadata == null) {
      return query;
    }

    LockModeType type = metadata.getLockModeType();
    TypedQuery<S> toReturn = type == null ? query : query.setLockMode(type);

    applyQueryHints(toReturn);

    return toReturn;*/
  }

  private <S, U extends T> Root<U> applySpecificationToCriteria(
      @Nullable Specification<U> spec, Class<U> domainClass, CriteriaQuery<S> query) {
    Root<U> root = query.from(domainClass);

    if (spec == null) {
      return root;
    }

    CriteriaBuilder builder = sessionFactory.getCriteriaBuilder();
    Predicate predicate = spec.toPredicate(root, query, builder);

    if (predicate != null) {
      query.where(predicate);
    }

    return root;
  }

  @SuppressWarnings("unused")
  /*interface SessionAwareReactiveJpaRepositoryImplementation<T, ID>
       extends ReactiveJpaRepositoryImplementation<T, ID> {

     <S extends T> Uni<List<S>> findAll(
         Stage.Session session, @Nullable Stage.Transaction transaction);

     <S extends T> Uni<S> findById(
         ID id, Stage.Session session, @Nullable Stage.Transaction transaction);

     <S extends T> Uni<S> save(
         S entity, Stage.Session session, @Nullable Stage.Transaction transaction);

     <S extends T> Uni<List<S>> saveAll(
         Iterable<S> entities, Stage.Session session, @Nullable Stage.Transaction transaction);

     Uni<Boolean> existsById(ID id, Stage.Session session, @Nullable Stage.Transaction
  transaction);

     <S extends T> Uni<List<S>> findAllById(
         Iterable<ID> ids, Stage.Session session, @Nullable Stage.Transaction transaction);

     Uni<Long> count(Stage.Session session, @Nullable Stage.Transaction transaction);

     Uni<Void> delete(T entity, Stage.Session session, @Nullable Stage.Transaction transaction);

     Uni<Void> deleteById(ID id, Stage.Session session, @Nullable Stage.Transaction transaction);

     Uni<Void> deleteAllById(
         Iterable<? extends ID> ids, Stage.Session session, @Nullable Stage.Transaction
  transaction);

     Uni<Void> deleteAll(
         Iterable<? extends T> entities,
         Stage.Session session,
         @Nullable Stage.Transaction transaction);

     Uni<List<T>> findAll(Sort sort, Stage.Session session, @Nullable Stage.Transaction
  transaction);

     Uni<List<T>> findAll(
         Pageable pageable, Stage.Session session, @Nullable Stage.Transaction transaction);
   }*/

  //    static class InternalRepository<T, ID> extends SimpleReactiveJpaRepository<T, ID>
  //      implements SessionAwareReactiveJpaRepositoryImplementation<T, ID> {
  //
  //    private static final ThreadPoolTaskExecutor EXECUTOR;
  //
  //    private final JpaEntityInformation<T, ?> entityInformation;
  //    private final Stage.SessionFactory sessionFactory;
  //
  //    static {
  //      EXECUTOR = new ThreadPoolTaskExecutor();
  //      EXECUTOR.setCorePoolSize(DEFAULT_POOL_SIZE);
  //      EXECUTOR.setMaxPoolSize(DEFAULT_POOL_SIZE * 2);
  //      //        EXECUTOR.setQueueCapacity(1000);
  //      EXECUTOR.setThreadNamePrefix("custom-parallel-");
  //      EXECUTOR.initialize();
  //    }
  //
  //    InternalRepository(
  //        JpaEntityInformation<T, ?> entityInformation, Stage.SessionFactory sessionFactory) {
  //      this.entityInformation = entityInformation;
  //      this.sessionFactory = sessionFactory;
  //    }
  //
  //    @Override
  //    public <S extends T> Uni<List<S>> findAll(
  //        Stage.Session session, Stage.Transaction transaction) {
  //      return getQuery(session, null, Sort.unsorted())
  //          .getResultList()
  //          .onItem()
  //          .transform(l -> (List<S>) l);
  //    }
  //
  //    @Override
  //    public <S extends T> Uni<S> findById(
  //        ID id, Stage.Session session, @Nullable Stage.Transaction transaction) {
  //      //      return
  //      //
  // toWrapper(session.find(SimpleReactiveJpaRepository.this.entityInformation.getJavaType(),
  //      // id), Mono.class);
  //      return session
  //          .find(entityInformation.getJavaType(), id)
  //          .onItem()
  //          .transform(e -> (S) e)
  //          .emitOn(EXECUTOR)
  //          .runSubscriptionOn(EXECUTOR);
  //    }
  //
  //    @Override
  //    public <S extends T> Uni<S> save(
  //        S entity, Stage.Session session, Stage.Transaction transaction) {
  //      return session
  //          .persist(entity)
  //          .chain(session::flush)
  //          .replaceWith(entity)
  //          .emitOn(EXECUTOR)
  //          .runSubscriptionOn(EXECUTOR);
  //      //      return Uni.createFrom().item(entity);
  //    }
  //
  //    @Override
  //    public <S extends T> Uni<List<S>> saveAll(
  //        Iterable<S> entities, Stage.Session session, Stage.Transaction transaction) {
  //      if (IterableUtils.isEmpty(entities)) {
  //        return
  // Uni.createFrom().<List<S>>nullItem().emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
  //      }
  //
  //      List<S> list = IterableUtils.toList(entities);
  //      return session
  //          .persistAll(list.toArray())
  //          .chain(session::flush)
  //          .replaceWith(list)
  //    .onItem().transformToMulti(Multi.createFrom()::iterable)
  //          .emitOn(EXECUTOR)
  //          .runSubscriptionOn(EXECUTOR);
  //    }
  //
  //    @Override
  //    public Uni<Boolean> existsById(ID id, Stage.Session session, Stage.Transaction transaction)
  // {
  //      if (entityInformation.getIdAttribute() == null) {
  //        return findById(id, session, transaction)
  //            .onItem()
  //            .transform(Objects::nonNull)
  //            .onItem()
  //            .ifNull()
  //            .continueWith(Boolean.FALSE)
  //            .emitOn(EXECUTOR)
  //            .runSubscriptionOn(EXECUTOR);
  //      }
  //
  //      String placeholder = "*";
  //      String entityName = entityInformation.getEntityName();
  //      Iterable<String> idAttributeNames = entityInformation.getIdAttributeNames();
  //      String existsQuery =
  //          QueryUtils.getExistsQueryString(entityName, placeholder, idAttributeNames);
  //
  //      Stage.SelectionQuery<Long> query = session.createQuery(existsQuery, Long.class);
  //
  //      //      applyQueryHints(query);
  //
  //      if (!entityInformation.hasCompositeId()) {
  //        query.setParameter(idAttributeNames.iterator().next(), id);
  //        return query
  //            .getSingleResult()
  //            .onItem()
  //            .ifNull()
  //            .continueWith(0L)
  //            .onItem()
  //            .transform(l -> l.equals(1L))
  //            .emitOn(EXECUTOR)
  //            .runSubscriptionOn(EXECUTOR);
  //      }
  //
  //      for (String idAttributeName : idAttributeNames) {
  //
  //        Object idAttributeValue =
  //            entityInformation.getCompositeIdAttributeValue(id, idAttributeName);
  //
  //        // TODO
  //        boolean complexIdParameterValueDiscovered = true
  //    idAttributeValue != null
  //    &&
  //
  // !query.getParameter(idAttributeName).getParameterType().isAssignableFrom(idAttributeValue.getClass())
  //    ;
  //
  //        if (complexIdParameterValueDiscovered) {
  //          // fall-back to findById(id) which does the proper mapping for the parameter.
  //          return findById(id, session, transaction)
  //              .onItem()
  //              .transform(Objects::nonNull)
  //              .onItem()
  //              .ifNull()
  //              .continueWith(Boolean.FALSE)
  //              .emitOn(EXECUTOR)
  //              .runSubscriptionOn(EXECUTOR);
  //        }
  //
  //        query.setParameter(idAttributeName, idAttributeValue);
  //      }
  //
  //      return query
  //          .getSingleResult()
  //          .onItem()
  //          .ifNull()
  //          .continueWith(0L)
  //          .onItem()
  //          .transform(l -> l.equals(1L))
  //          .emitOn(EXECUTOR)
  //          .runSubscriptionOn(EXECUTOR);
  //    }
  //
  //    @Override
  //    public <S extends T> Uni<List<S>> findAllById(
  //        Iterable<ID> ids, Stage.Session session, Stage.Transaction transaction) {
  //      if (IterableUtils.isEmpty(ids)) {
  //        return Uni.createFrom()
  //            .item(Collections.<S>emptyList())
  //            .emitOn(EXECUTOR)
  //            .runSubscriptionOn(EXECUTOR);
  //      }
  //
  //      // IllegalStateException: Illegal pop() with non-matching JdbcValuesSourceProcessingState
  //      if (entityInformation.hasCompositeId()) {
  //        Iterator<ID> iterator = ids.iterator();
  //        Uni<List<S>> uni =
  //            findById(iterator.next(), session, transaction)
  //                .map(e -> (List<S>) new ArrayList<>(List.of(e)));
  //        while (iterator.hasNext()) {
  //          ID next = iterator.next();
  //          uni =
  //              uni.flatMap(
  //                  l ->
  //                      findById(next, session, transaction)
  //                          .map(
  //                              e -> {
  //                                List<S> rs = new ArrayList<>(e == null ? l.size() : l.size() +
  // 1);
  //                                rs.addAll(l);
  //                                if (e != null) {
  //                                  rs.add((S) e);
  //                                }
  //                                return rs;
  //                              }));
  //        }
  //
  //        return uni.emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
  //      }
  //
  //      Collection<ID> idCollection = Streamable.of(ids).toList();
  //
  //      ByIdsSpecification<T> specification = new ByIdsSpecification<>(entityInformation);
  //      Stage.SelectionQuery<T> query = getQuery(session, specification, Sort.unsorted());
  //
  //      return query
  //          .setParameter(specification.parameter, idCollection)
  //          .getResultList()
  //          .onItem()
  //          .transform(l -> (List<S>) l)
  //          .emitOn(EXECUTOR)
  //          .runSubscriptionOn(EXECUTOR);
  //    }
  //
  //    @Override
  //    public Uni<Long> count(Stage.Session session, Stage.Transaction transaction) {
  //      Stage.SelectionQuery<Long> query = session.createQuery(getCountQueryString(), Long.class);
  //      //      applyQueryHintsForCount(query);
  //
  //      return query.getSingleResult().emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
  //    }
  //
  //    @Override
  //    public Uni<Void> delete(T entity, Stage.Session session, Stage.Transaction transaction) {
  //      if (entityInformation.isNew(entity)) {
  //        return Uni.createFrom().voidItem().emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
  //      }
  //
  //      Class<?> type = ProxyUtils.getUserClass(entity);
  //      return session
  //          .find(type, entityInformation.getId(entity))
  //          .onItem()
  //          .ifNotNull()
  //          .transformToUni(
  //              e -> {
  //                if (session.contains(e)) {
  //                  return session.remove(e).chain(session::flush);
  //                }
  //                return session
  //                    .merge(e)
  //                    .onItem()
  //                    .transformToUni(session::remove)
  //                    .chain(session::flush);
  //              })
  //          .emitOn(EXECUTOR)
  //          .runSubscriptionOn(EXECUTOR);
  //    return session.refresh(entity).chain(() -> {
  //      if(session.contains(entity)) {
  //        return session.remove(entity);
  //      }
  //      return
  //   session.merge(entity).chain(session::remove).emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
  //    });
  //    }
  //
  //    @Override
  //    public Uni<Void> deleteById(ID id, Stage.Session session, Stage.Transaction transaction) {
  //      return findById(id, session, transaction)
  //          .onItem()
  //          .ifNotNull()
  //          .transformToUni(e -> delete(e, session, transaction))
  //          .emitOn(EXECUTOR)
  //          .runSubscriptionOn(EXECUTOR);
  //    }
  //
  //    @Override
  //    public Uni<Void> deleteAllById(
  //        Iterable<? extends ID> ids, Stage.Session session, Stage.Transaction transaction) {
  //      if (IterableUtils.isEmpty(ids)) {
  //        return Uni.createFrom().voidItem().emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
  //      }
  //
  //      Iterator<? extends ID> iterator = ids.iterator();
  //      Uni<Void> uni = deleteById(iterator.next(), session, transaction);
  //      while (iterator.hasNext()) {
  //        ID id = iterator.next();
  //        uni = uni.chain(v -> deleteById(id, session, transaction));
  //      }
  //
  //      return uni.emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
  //    }
  //
  //    @Override
  //    public Uni<Void> deleteAll(
  //        Iterable<? extends T> entities, Stage.Session session, Stage.Transaction transaction) {
  //      if (IterableUtils.isEmpty(entities)) {
  //        return Uni.createFrom().voidItem().emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
  //      }
  //
  //      Iterator<? extends T> iterator = entities.iterator();
  //      Uni<Void> uni = delete(iterator.next(), session, transaction);
  //      while (iterator.hasNext()) {
  //        T next = iterator.next();
  //        uni = uni.chain(v -> delete(next, session, transaction));
  //      }
  //
  //      return uni.emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
  //    }
  //
  //    @Override
  //    public Uni<List<T>> findAll(Sort sort, Stage.Session session, Stage.Transaction transaction)
  // {
  //      return getQuery(session, null, sort)
  //          .getResultList()
  //          .emitOn(EXECUTOR)
  //          .runSubscriptionOn(EXECUTOR);
  //    }
  //
  //    @Override
  //    public Uni<List<T>> findAll(
  //        Pageable pageable, Stage.Session session, Stage.Transaction transaction) {
  //      if (pageable.isUnpaged()) {
  //        return findAll(session, transaction).emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
  //      }
  //
  //      return findAll(session, null, pageable).emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
  //    }
  //
  //    public Uni<List<T>> findAll(Stage.Session session, Specification<T> spec, Pageable pageable)
  // {
  //      Stage.SelectionQuery<T> query = getQuery(session, spec, pageable);
  //      return pageable.isUnpaged()
  //          ? query.getResultList().emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR)
  //          : readPage(query, entityInformation.getJavaType(), pageable, spec)
  //              .emitOn(EXECUTOR)
  //              .runSubscriptionOn(EXECUTOR);
  //    }
  //
  //    private Uni<List<T>> readPage(
  //        Stage.SelectionQuery<T> query,
  //        Class<T> javaType,
  //        Pageable pageable,
  //        Specification<T> spec) {
  //      if (pageable.isPaged()) {
  //        query.setFirstResult(PageableUtils.getOffsetAsInteger(pageable));
  //        query.setMaxResults(pageable.getPageSize());
  //      }
  //
  //      return query.getResultList().emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
  //    }
  //
  //    protected Stage.SelectionQuery<T> getQuery(
  //        Stage.Session session, @Nullable Specification<T> spec, Pageable pageable) {
  //      Sort sort = pageable.isPaged() ? pageable.getSort() : Sort.unsorted();
  //      return getQuery(session, spec, entityInformation.getJavaType(), sort);
  //    }
  //
  //    private String getCountQueryString() {
  //      String countQuery = String.format(COUNT_QUERY_STRING, "*", "%s");
  //      return getQueryString(countQuery, entityInformation.getEntityName());
  //    }
  //
  //    protected Stage.SelectionQuery<T> getQuery(
  //        Stage.Session session, @Nullable Specification<T> spec, Sort sort) {
  //      return getQuery(session, spec, entityInformation.getJavaType(), sort);
  //    }
  //
  //    protected <S extends T> Stage.SelectionQuery<S> getQuery(
  //        Stage.Session session, Specification<S> spec, Class<S> domainClass, Sort sort) {
  //      CriteriaBuilder builder = sessionFactory.getCriteriaBuilder();
  //      CriteriaQuery<S> query = builder.createQuery(domainClass);
  //
  //      Root<S> root = applySpecificationToCriteria(spec, domainClass, query);
  //      query.select(root);
  //
  //      if (sort.isSorted()) {
  //        query.orderBy(toOrders(sort, root, builder));
  //      }
  //
  //      return applyRepositoryMethodMetadata(session.createQuery(query));
  //    }
  //
  //    private <S> Stage.SelectionQuery<S> applyRepositoryMethodMetadata(
  //        Stage.SelectionQuery<S> query) {
  //      // TODO
  //      return query;
  //    /*if (metadata == null) {
  //      return query;
  //    }
  //
  //    LockModeType type = metadata.getLockModeType();
  //    TypedQuery<S> toReturn = type == null ? query : query.setLockMode(type);
  //
  //    applyQueryHints(toReturn);
  //
  //    return toReturn;*/
  //      }
  //
  //      private <S, U extends T> Root<U> applySpecificationToCriteria(
  //          @Nullable Specification<U> spec, Class<U> domainClass, CriteriaQuery<S> query) {
  //        Root<U> root = query.from(domainClass);
  //
  //        if (spec == null) {
  //          return root;
  //        }
  //
  //        CriteriaBuilder builder = sessionFactory.getCriteriaBuilder();
  //        Predicate predicate = spec.toPredicate(root, query, builder);
  //
  //        if (predicate != null) {
  //          query.where(predicate);
  //        }
  //
  //        return root;
  //      }
  //    }

  static final class ByIdsSpecification<T> implements Specification<T> {

    @Serial private static final long serialVersionUID = 1L;

    private final JpaEntityInformation<T, ?> entityInformation;

    @Nullable ParameterExpression<Collection<?>> parameter;

    ByIdsSpecification(JpaEntityInformation<T, ?> entityInformation) {
      this.entityInformation = entityInformation;
    }

    @Override
    public Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
      Path<?> path = root.get(entityInformation.getIdAttribute());
      parameter =
          (ParameterExpression<Collection<?>>) (ParameterExpression) cb.parameter(Collection.class);
      return path.in(parameter);
    }
  }
}
