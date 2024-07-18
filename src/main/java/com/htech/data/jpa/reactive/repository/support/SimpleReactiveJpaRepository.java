package com.htech.data.jpa.reactive.repository.support;

import static org.springframework.data.jpa.repository.query.QueryUtils.*;

import com.htech.data.jpa.reactive.core.StageReactiveJpaEntityOperations;
import com.htech.data.jpa.reactive.repository.query.Jpa21Utils;
import com.htech.data.jpa.reactive.repository.query.QueryUtils;
import com.htech.jpa.reactive.connection.SessionContextHolder;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import jakarta.persistence.criteria.*;
import java.io.Serial;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.stream.StreamSupport;
import org.apache.commons.collections4.IterableUtils;
import org.hibernate.reactive.stage.Stage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.query.JpaEntityGraph;
import org.springframework.data.jpa.repository.support.CrudMethodMetadata;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.MutableQueryHints;
import org.springframework.data.jpa.repository.support.QueryHints;
import org.springframework.data.jpa.support.PageableUtils;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.data.util.Optionals;
import org.springframework.data.util.ProxyUtils;
import org.springframework.data.util.Streamable;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Bao.Ngo
 */
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
        .zipWhen(__ -> CrudMethodMetadataContextHolder.currentCrudMethodMetadata())
        .flatMap(
            t ->
                Mono.defer(
                    () -> {
                      CompletionStage<List<T>> resultList =
                          getQuery(t.getT1(), null, Sort.unsorted(), t.getT2()).getResultList();
                      return Mono.fromCompletionStage(resultList);
                    }))
        .flatMapMany(Flux::fromIterable)
        .map(e -> (S) e);
  }

  @Override
  public <S extends T> Mono<S> findById(ID id) {
    return SessionContextHolder.currentSession()
        .zipWhen(__ -> CrudMethodMetadataContextHolder.currentCrudMethodMetadata())
        .flatMap(
            t -> {
              Stage.Session session = t.getT1();
              LockModeType lockModeType = t.getT2().getLockModeType();
              Mono<T> rs;
              // TODO: entity graph??
              if (lockModeType == null) {
                rs = Mono.defer(() -> Mono.fromCompletionStage(session.find(getDomainClass(), id)));
              } else {
                rs =
                    Mono.defer(
                        () ->
                            Mono.fromCompletionStage(
                                session.find(getDomainClass(), id, lockModeType)));
              }

              return rs.map(e -> (S) e);
            });
  }

  @Override
  public Mono<T> getReferenceById(ID id) {
    return SessionContextHolder.currentSession()
        .map(session -> session.getReference(getDomainClass(), id));
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
        .zipWhen(__ -> CrudMethodMetadataContextHolder.currentCrudMethodMetadata())
        .flatMap(
            t ->
                Mono.defer(
                    () -> {
                      Collection<ID> idCollection = Streamable.of(ids).toList();
                      ByIdsSpecification<T> specification =
                          new ByIdsSpecification<>(entityInformation);
                      Stage.SelectionQuery<T> query =
                          getQuery(t.getT1(), specification, Sort.unsorted(), t.getT2());

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
        .zipWhen(__ -> CrudMethodMetadataContextHolder.currentCrudMethodMetadata())
        .flatMap(
            t ->
                Mono.defer(
                    () -> {
                      Stage.SelectionQuery<T> query = getQuery(t.getT1(), null, sort, t.getT2());
                      return Mono.fromCompletionStage(query.getResultList());
                    }))
        .flatMapMany(Flux::fromIterable);
  }

  @Override
  public Mono<Page<T>> findAll(Pageable pageable) {
    if (pageable.isUnpaged()) {
      return findAll().collectList().map(PageImpl::new);
    }

    return findAll(SessionContextHolder.currentSession(), null, pageable);
  }

  @Override
  public Mono<T> findOne(Specification<T> spec) {
    return SessionContextHolder.currentSession()
        .zipWhen(__ -> CrudMethodMetadataContextHolder.currentCrudMethodMetadata())
        .flatMap(
            t ->
                Mono.defer(
                    () ->
                        Mono.fromCompletionStage(
                                getQuery(t.getT1(), spec, Sort.unsorted(), t.getT2())
                                    .setMaxResults(2)
                                    .getSingleResult())
                            .onErrorResume(NoResultException.class, e -> Mono.empty())));
  }

  @Override
  public Flux<T> findAll(Specification<T> spec) {
    return findAll(SessionContextHolder.currentSession(), spec, Pageable.unpaged())
        .flatMapMany(page -> Flux.fromIterable(page.getContent()));
  }

  @Override
  public Flux<T> findAll(Specification<T> spec, Sort sort) {
    return SessionContextHolder.currentSession()
        .zipWhen(__ -> CrudMethodMetadataContextHolder.currentCrudMethodMetadata())
        .flatMap(
            t ->
                Mono.defer(
                    () ->
                        Mono.fromCompletionStage(
                            getQuery(t.getT1(), spec, sort, t.getT2()).getResultList())))
        .flatMapMany(Flux::fromIterable);
  }

  @Override
  public Mono<List<T>> findAllToList(Specification<T> spec) {
    return findAll(SessionContextHolder.currentSession(), spec, Pageable.unpaged())
        .map(Page::getContent);
  }

  @Override
  public Mono<Page<T>> findAll(Specification<T> spec, Pageable pageable) {
    return findAll(SessionContextHolder.currentSession(), spec, pageable);
  }

  @Override
  public Mono<Long> count(Specification<T> spec) {
    return SessionContextHolder.currentSession()
        .flatMap(s -> executeCountQuery(getCountQuery(s, spec, getDomainClass())));
  }

  @Override
  public Mono<Boolean> exists(Specification<T> spec) {
    return SessionContextHolder.currentSession()
        .zipWhen(__ -> CrudMethodMetadataContextHolder.currentCrudMethodMetadata())
        .flatMap(
            t -> {
              Stage.Session session = t.getT1();
              CriteriaQuery<Integer> cq =
                  sessionFactory
                      .getCriteriaBuilder()
                      .createQuery(Integer.class)
                      .select(sessionFactory.getCriteriaBuilder().literal(1));

              applySpecificationToCriteria(spec, getDomainClass(), cq);

              return Mono.defer(
                  () ->
                      Mono.fromCompletionStage(
                              applyRepositoryMethodMetadata(
                                      session.createQuery(cq), session, t.getT2())
                                  .setMaxResults(1)
                                  .getResultList())
                          .map(l -> l.size() == 1));
            });
  }

  @Override
  public Mono<Long> delete(Specification<T> spec) {
    return SessionContextHolder.currentSession()
        .flatMap(
            s -> {
              CriteriaBuilder builder = sessionFactory.getCriteriaBuilder();
              CriteriaDelete<T> delete = builder.createCriteriaDelete(getDomainClass());

              if (spec != null) {
                Predicate predicate =
                    spec.toPredicate(delete.from(getDomainClass()), null, builder);

                if (predicate != null) {
                  delete.where(predicate);
                }
              }

              return Mono.defer(
                      () -> Mono.fromCompletionStage(s.createQuery(delete).executeUpdate()))
                  .map(i -> (long) i);
            });
  }

  protected Class<T> getDomainClass() {
    return entityInformation.getJavaType();
  }

  protected Mono<Page<T>> findAll(
      Mono<Stage.Session> session, Specification<T> spec, Pageable pageable) {
    return session
        .zipWhen(__ -> CrudMethodMetadataContextHolder.currentCrudMethodMetadata())
        .flatMap(
            t ->
                Mono.defer(
                    () -> {
                      Stage.SelectionQuery<T> query =
                          getQuery(t.getT1(), spec, pageable, t.getT2());
                      return pageable.isUnpaged()
                          ? Mono.fromCompletionStage(query.getResultList()).map(PageImpl::new)
                          : readPage(query, getDomainClass(), pageable, spec, t.getT1());
                    }));
  }

  private static Mono<Void> deferRemoving(Stage.Session session, Object e) {
    return Mono.defer(() -> Mono.fromCompletionStage(session.remove(e)));
  }

  private static Mono<Void> deferFlushing(Stage.Session session) {
    return Mono.defer(() -> Mono.fromCompletionStage(session.flush()));
  }

  private Mono<Page<T>> readPage(
      Stage.SelectionQuery<T> query,
      Class<T> javaType,
      Pageable pageable,
      Specification<T> spec,
      Stage.Session session) {
    if (pageable.isPaged()) {
      query.setFirstResult(PageableUtils.getOffsetAsInteger(pageable));
      query.setMaxResults(pageable.getPageSize());
    }

    return Mono.defer(() -> Mono.fromCompletionStage(query.getResultList()))
        .zipWhen(__ -> executeCountQuery(getCountQuery(session, spec, javaType)))
        .map(t -> PageableExecutionUtils.getPage(t.getT1(), pageable, t::getT2));
  }

  protected Stage.SelectionQuery<T> getQuery(
      Stage.Session session,
      @Nullable Specification<T> spec,
      Pageable pageable,
      CrudMethodMetadata metadata) {
    Sort sort = pageable.isPaged() ? pageable.getSort() : Sort.unsorted();
    return getQuery(session, spec, getDomainClass(), sort, metadata);
  }

  private String getCountQueryString() {
    String countQuery = String.format(COUNT_QUERY_STRING, "*", "%s");
    return getQueryString(countQuery, entityInformation.getEntityName());
  }

  protected <S extends T> Stage.SelectionQuery<Long> getCountQuery(
      Stage.Session session, @Nullable Specification<S> spec, Class<S> domainClass) {
    CriteriaBuilder builder = sessionFactory.getCriteriaBuilder();
    CriteriaQuery<Long> query = builder.createQuery(Long.class);

    Root<S> root = applySpecificationToCriteria(spec, domainClass, query);

    if (query.isDistinct()) {
      query.select(builder.countDistinct(root));
    } else {
      query.select(builder.count(root));
    }

    // Remove all Orders the Specifications might have applied
    query.orderBy(Collections.emptyList());

    return applyRepositoryMethodMetadataForCount(session.createQuery(query));
  }

  protected Stage.SelectionQuery<T> getQuery(
      Stage.Session session,
      @Nullable Specification<T> spec,
      Sort sort,
      CrudMethodMetadata metadata) {
    return getQuery(session, spec, getDomainClass(), sort, metadata);
  }

  protected <S extends T> Stage.SelectionQuery<S> getQuery(
      Stage.Session session,
      Specification<S> spec,
      Class<S> domainClass,
      Sort sort,
      CrudMethodMetadata metadata) {
    CriteriaBuilder builder = sessionFactory.getCriteriaBuilder();
    CriteriaQuery<S> query = builder.createQuery(domainClass);

    Root<S> root = applySpecificationToCriteria(spec, domainClass, query);
    query.select(root);

    if (sort.isSorted()) {
      query.orderBy(toOrders(sort, root, builder));
    }

    return applyRepositoryMethodMetadata(session.createQuery(query), session, metadata);
  }

  private <S> Stage.SelectionQuery<S> applyRepositoryMethodMetadataForCount(
      Stage.SelectionQuery<S> query) {

    // TODO
    //    if (metadata == null) {
    //      return query;
    //    }
    //
    //    applyQueryHintsForCount(query);

    return query;
  }

  private static Mono<Long> executeCountQuery(Stage.SelectionQuery<Long> query) {
    return Mono.defer(() -> Mono.fromCompletionStage(query.getResultList()))
        .map(l -> l.stream().reduce(0L, Long::sum));
    //    long total = 0L;
    //
    //    for (Long element : totals) {
    //      total += element == null ? 0 : element;
    //    }
    //
    //    return total;
  }

  private <S> Stage.SelectionQuery<S> applyRepositoryMethodMetadata(
      Stage.SelectionQuery<S> query, Stage.Session session, CrudMethodMetadata metadata) {
    LockModeType lockModeType = metadata.getLockModeType();
    if (lockModeType != null) {
      query.setLockMode(lockModeType);
    }

    QueryHints queryHintsForEntityGraphs =
        Optionals.mapIfAllPresent(
                Optional.of(session),
                metadata.getEntityGraph(),
                (s, graph) ->
                    Jpa21Utils.getFetchGraphHint(
                        s, getEntityGraph(graph, metadata), getDomainClass()))
            .orElseGet(MutableQueryHints::new);
    queryHintsForEntityGraphs.forEach(
        (k, v) -> query.setPlan((jakarta.persistence.EntityGraph<S>) v));

    return query;
  }

  private JpaEntityGraph getEntityGraph(EntityGraph entityGraph, CrudMethodMetadata metadata) {
    String fallbackName = entityInformation.getEntityName() + "." + metadata.getMethod().getName();

    return new JpaEntityGraph(entityGraph, fallbackName);
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
          (ParameterExpression<Collection<?>>)
              (ParameterExpression<?>) cb.parameter(Collection.class);
      return path.in(parameter);
    }
  }
}
