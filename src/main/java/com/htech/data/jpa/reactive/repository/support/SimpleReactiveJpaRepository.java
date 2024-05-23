package com.htech.data.jpa.reactive.repository.support;

import static org.springframework.data.jpa.repository.query.QueryUtils.*;
import static reactor.core.scheduler.Schedulers.DEFAULT_POOL_SIZE;

import com.htech.data.jpa.reactive.core.MutinyReactiveJpaEntityOperations;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.criteria.*;
import java.io.Serial;
import java.util.*;
import org.apache.commons.collections4.IterableUtils;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.support.PageableUtils;
import org.springframework.data.util.ProxyUtils;
import org.springframework.data.util.Streamable;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
public class SimpleReactiveJpaRepository<T, ID>
    implements ReactiveJpaRepositoryImplementation<T, ID> {

  public static <T, ID> ReactiveJpaRepositoryImplementation<T, ID> createInstance(
      JpaEntityInformation<T, ?> entityInformation,
      MutinyReactiveJpaEntityOperations entityOperations,
      Mutiny.SessionFactory sessionFactory,
      ClassLoader classLoader) {
    SimpleReactiveJpaRepository<T, ID> instance =
        new InternalRepository<>(entityInformation, entityOperations, sessionFactory /*, classLoader*/);
    ProxyFactory proxyFactory = new ProxyFactory(instance);

    return (ReactiveJpaRepositoryImplementation<T, ID>) proxyFactory.getProxy(classLoader);
  }

  @Override
  public <S extends T> Flux<S> findAll() {
    return Flux.empty();
  }

  @Override
  public <S extends T> Mono<S> findById(ID id) {
    return Mono.empty();
  }

  @Override
  public <S extends T> Mono<S> save(S entity) {
    return Mono.empty();
  }

  @Override
  public <S extends T> Flux<S> saveAll(Iterable<S> entities) {
    return Flux.empty();
  }

  @Override
  public Mono<Boolean> existsById(ID id) {
    return Mono.empty();
  }

  @Override
  public <S extends T> Flux<S> findAllById(Iterable<ID> ids) {
    return Flux.empty();
  }

  @Override
  public Mono<Long> count() {
    return Mono.empty();
  }

  @Override
  public Mono<Void> deleteById(ID id) {
    return Mono.empty();
  }

  @Override
  public Mono<Void> delete(T entity) {
    return Mono.empty();
  }

  @Override
  public Mono<Void> deleteAllById(Iterable<? extends ID> ids) {
    return Mono.empty();
  }

  @Override
  public Mono<Void> deleteAll(Iterable<? extends T> entities) {
    return Mono.empty();
  }

  @Override
  public Flux<T> findAll(Sort sort) {
    return Flux.empty();
  }

  @Override
  public Flux<T> findAll(Pageable pageable) {
    return Flux.empty();
  }

  @SuppressWarnings("unused")
  interface SessionAwareReactiveJpaRepositoryImplementation<T, ID>
      extends ReactiveJpaRepositoryImplementation<T, ID> {

    <S extends T> Uni<List<S>> findAll(
        Mutiny.Session session, @Nullable Mutiny.Transaction transaction);

    <S extends T> Uni<S> findById(
        ID id, Mutiny.Session session, @Nullable Mutiny.Transaction transaction);

    <S extends T> Uni<S> save(
        S entity, Mutiny.Session session, @Nullable Mutiny.Transaction transaction);

    <S extends T> Uni<List<S>> saveAll(
        Iterable<S> entities, Mutiny.Session session, @Nullable Mutiny.Transaction transaction);

    Uni<Boolean> existsById(
        ID id, Mutiny.Session session, @Nullable Mutiny.Transaction transaction);

    <S extends T> Uni<List<S>> findAllById(
        Iterable<ID> ids, Mutiny.Session session, @Nullable Mutiny.Transaction transaction);

    Uni<Long> count(Mutiny.Session session, @Nullable Mutiny.Transaction transaction);

    Uni<Void> delete(T entity, Mutiny.Session session, @Nullable Mutiny.Transaction transaction);

    Uni<Void> deleteById(ID id, Mutiny.Session session, @Nullable Mutiny.Transaction transaction);

    Uni<Void> deleteAllById(
        Iterable<? extends ID> ids,
        Mutiny.Session session,
        @Nullable Mutiny.Transaction transaction);

    Uni<Void> deleteAll(
        Iterable<? extends T> entities,
        Mutiny.Session session,
        @Nullable Mutiny.Transaction transaction);

    Uni<List<T>> findAll(
        Sort sort, Mutiny.Session session, @Nullable Mutiny.Transaction transaction);

    Uni<List<T>> findAll(
        Pageable pageable, Mutiny.Session session, @Nullable Mutiny.Transaction transaction);
  }

  static class InternalRepository<T, ID> extends SimpleReactiveJpaRepository<T, ID>
      implements SessionAwareReactiveJpaRepositoryImplementation<T, ID> {

    private static final ThreadPoolTaskExecutor EXECUTOR;

    private final JpaEntityInformation<T, ?> entityInformation;
    private final MutinyReactiveJpaEntityOperations entityOperations;
    private final Mutiny.SessionFactory sessionFactory;

    static {
      EXECUTOR = new ThreadPoolTaskExecutor();
      EXECUTOR.setCorePoolSize(DEFAULT_POOL_SIZE);
      EXECUTOR.setMaxPoolSize(DEFAULT_POOL_SIZE * 2);
      EXECUTOR.setThreadNamePrefix("custom-parallel-");
      EXECUTOR.initialize();
    }

    InternalRepository(
        JpaEntityInformation<T, ?> entityInformation, MutinyReactiveJpaEntityOperations entityOperations, Mutiny.SessionFactory sessionFactory) {
      this.entityInformation = entityInformation;
      this.entityOperations = entityOperations;
      this.sessionFactory = sessionFactory;
    }

    @Override
    public <S extends T> Uni<List<S>> findAll(
        Mutiny.Session session, Mutiny.Transaction transaction) {
      return getQuery(session, null, Sort.unsorted())
          .getResultList()
          .onItem()
          .transform(l -> (List<S>) l);
    }

    @Override
    public <S extends T> Uni<S> findById(
        ID id, Mutiny.Session session, @Nullable Mutiny.Transaction transaction) {
      return session
          .find(entityInformation.getJavaType(), id)
          .onItem()
          .transform(e -> (S) e)
          .emitOn(EXECUTOR)
          .runSubscriptionOn(EXECUTOR);
    }

    @Override
    public <S extends T> Uni<S> save(
        S entity, Mutiny.Session session, Mutiny.Transaction transaction) {
      return entityOperations.persist(entity, session, transaction);
//      return session
//          .persist(entity)
//          .chain(session::flush)
//          .replaceWith(entity)
//          .emitOn(EXECUTOR)
//          .runSubscriptionOn(EXECUTOR);
    }

    @Override
    public <S extends T> Uni<List<S>> saveAll(
        Iterable<S> entities, Mutiny.Session session, Mutiny.Transaction transaction) {
      if (IterableUtils.isEmpty(entities)) {
        return Uni.createFrom().<List<S>>nullItem().emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
      }

      List<S> list = IterableUtils.toList(entities);
      return session
          .persistAll(list.toArray())
          .chain(session::flush)
          .replaceWith(list) /*
          .onItem().transformToMulti(Multi.createFrom()::iterable)*/
          .emitOn(EXECUTOR)
          .runSubscriptionOn(EXECUTOR);
    }

    @Override
    public Uni<Boolean> existsById(ID id, Mutiny.Session session, Mutiny.Transaction transaction) {
      if (entityInformation.getIdAttribute() == null) {
        return findById(id, session, transaction)
            .onItem()
            .transform(Objects::nonNull)
            .onItem()
            .ifNull()
            .continueWith(Boolean.FALSE)
            .emitOn(EXECUTOR)
            .runSubscriptionOn(EXECUTOR);
      }

      String placeholder = "*";
      String entityName = entityInformation.getEntityName();
      Iterable<String> idAttributeNames = entityInformation.getIdAttributeNames();
      String existsQuery =
          QueryUtils.getExistsQueryString(entityName, placeholder, idAttributeNames);

      Mutiny.SelectionQuery<Long> query = session.createQuery(existsQuery, Long.class);

      //      applyQueryHints(query);

      if (!entityInformation.hasCompositeId()) {
        query.setParameter(idAttributeNames.iterator().next(), id);
        return query
            .getSingleResult()
            .onItem()
            .ifNull()
            .continueWith(0L)
            .onItem()
            .transform(l -> l.equals(1L))
            .emitOn(EXECUTOR)
            .runSubscriptionOn(EXECUTOR);
      }

      for (String idAttributeName : idAttributeNames) {

        Object idAttributeValue =
            entityInformation.getCompositeIdAttributeValue(id, idAttributeName);

        // TODO
        boolean complexIdParameterValueDiscovered = true /*idAttributeValue != null
            && !query.getParameter(idAttributeName).getParameterType().isAssignableFrom(idAttributeValue.getClass())*/;

        if (complexIdParameterValueDiscovered) {
          // fall-back to findById(id) which does the proper mapping for the parameter.
          return findById(id, session, transaction)
              .onItem()
              .transform(Objects::nonNull)
              .onItem()
              .ifNull()
              .continueWith(Boolean.FALSE)
              .emitOn(EXECUTOR)
              .runSubscriptionOn(EXECUTOR);
        }

        query.setParameter(idAttributeName, idAttributeValue);
      }

      return query
          .getSingleResult()
          .onItem()
          .ifNull()
          .continueWith(0L)
          .onItem()
          .transform(l -> l.equals(1L))
          .emitOn(EXECUTOR)
          .runSubscriptionOn(EXECUTOR);
    }

    @Override
    public <S extends T> Uni<List<S>> findAllById(
        Iterable<ID> ids, Mutiny.Session session, Mutiny.Transaction transaction) {
      if (IterableUtils.isEmpty(ids)) {
        return Uni.createFrom()
            .item(Collections.<S>emptyList())
            .emitOn(EXECUTOR)
            .runSubscriptionOn(EXECUTOR);
      }

      // IllegalStateException: Illegal pop() with non-matching JdbcValuesSourceProcessingState
      if (entityInformation.hasCompositeId()) {
        Iterator<ID> iterator = ids.iterator();
        Uni<List<S>> uni =
            findById(iterator.next(), session, transaction)
                .map(e -> (List<S>) new ArrayList<>(List.of(e)));
        while (iterator.hasNext()) {
          ID next = iterator.next();
          uni =
              uni.flatMap(
                  l ->
                      findById(next, session, transaction)
                          .map(
                              e -> {
                                List<S> rs = new ArrayList<>(e == null ? l.size() : l.size() + 1);
                                rs.addAll(l);
                                if (e != null) {
                                  rs.add((S) e);
                                }
                                return rs;
                              }));
        }

        return uni.emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
      }

      Collection<ID> idCollection = Streamable.of(ids).toList();

      ByIdsSpecification<T> specification = new ByIdsSpecification<>(entityInformation);
      Mutiny.SelectionQuery<T> query = getQuery(session, specification, Sort.unsorted());

      return query
          .setParameter(specification.parameter, idCollection)
          .getResultList()
          .onItem()
          .transform(l -> (List<S>) l)
          .emitOn(EXECUTOR)
          .runSubscriptionOn(EXECUTOR);
    }

    @Override
    public Uni<Long> count(Mutiny.Session session, Mutiny.Transaction transaction) {
      Mutiny.SelectionQuery<Long> query = session.createQuery(getCountQueryString(), Long.class);
      //      applyQueryHintsForCount(query);

      return query.getSingleResult().emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
    }

    @Override
    public Uni<Void> delete(T entity, Mutiny.Session session, Mutiny.Transaction transaction) {
      if (entityInformation.isNew(entity)) {
        return Uni.createFrom().voidItem().emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
      }

      Class<?> type = ProxyUtils.getUserClass(entity);
      return session
          .find(type, entityInformation.getId(entity))
          .onItem()
          .ifNotNull()
          .transformToUni(
              e -> {
                if (session.contains(e)) {
                  return session.remove(e).chain(session::flush);
                }
                return session
                    .merge(e)
                    .onItem()
                    .transformToUni(session::remove)
                    .chain(session::flush);
              })
          .emitOn(EXECUTOR)
          .runSubscriptionOn(EXECUTOR);
      /*return session.refresh(entity).chain(() -> {
        if(session.contains(entity)) {
          return session.remove(entity);
        }
        return session.merge(entity).chain(session::remove).emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
      });*/
    }

    @Override
    public Uni<Void> deleteById(ID id, Mutiny.Session session, Mutiny.Transaction transaction) {
      return findById(id, session, transaction)
          .onItem()
          .ifNotNull()
          .transformToUni(e -> delete(e, session, transaction))
          .emitOn(EXECUTOR)
          .runSubscriptionOn(EXECUTOR);
    }

    @Override
    public Uni<Void> deleteAllById(
        Iterable<? extends ID> ids, Mutiny.Session session, Mutiny.Transaction transaction) {
      if (IterableUtils.isEmpty(ids)) {
        return Uni.createFrom().voidItem().emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
      }

      Iterator<? extends ID> iterator = ids.iterator();
      Uni<Void> uni = deleteById(iterator.next(), session, transaction);
      while (iterator.hasNext()) {
        ID id = iterator.next();
        uni = uni.chain(v -> deleteById(id, session, transaction));
      }

      return uni.emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
    }

    @Override
    public Uni<Void> deleteAll(
        Iterable<? extends T> entities, Mutiny.Session session, Mutiny.Transaction transaction) {
      if (IterableUtils.isEmpty(entities)) {
        return Uni.createFrom().voidItem().emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
      }

      Iterator<? extends T> iterator = entities.iterator();
      Uni<Void> uni = delete(iterator.next(), session, transaction);
      while (iterator.hasNext()) {
        T next = iterator.next();
        uni = uni.chain(v -> delete(next, session, transaction));
      }

      return uni.emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
    }

    @Override
    public Uni<List<T>> findAll(Sort sort, Mutiny.Session session, Mutiny.Transaction transaction) {
      return getQuery(session, null, sort)
          .getResultList()
          .emitOn(EXECUTOR)
          .runSubscriptionOn(EXECUTOR);
    }

    @Override
    public Uni<List<T>> findAll(
        Pageable pageable, Mutiny.Session session, Mutiny.Transaction transaction) {
      if (pageable.isUnpaged()) {
        return findAll(session, transaction).emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
      }

      return findAll(session, null, pageable).emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
    }

    public Uni<List<T>> findAll(Mutiny.Session session, Specification<T> spec, Pageable pageable) {
      Mutiny.SelectionQuery<T> query = getQuery(session, spec, pageable);
      return pageable.isUnpaged()
          ? query.getResultList().emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR)
          : readPage(query, entityInformation.getJavaType(), pageable, spec)
              .emitOn(EXECUTOR)
              .runSubscriptionOn(EXECUTOR);
    }

    private Uni<List<T>> readPage(
        Mutiny.SelectionQuery<T> query,
        Class<T> javaType,
        Pageable pageable,
        Specification<T> spec) {
      if (pageable.isPaged()) {
        query.setFirstResult(PageableUtils.getOffsetAsInteger(pageable));
        query.setMaxResults(pageable.getPageSize());
      }

      return query.getResultList().emitOn(EXECUTOR).runSubscriptionOn(EXECUTOR);
    }

    protected Mutiny.SelectionQuery<T> getQuery(
        Mutiny.Session session, @Nullable Specification<T> spec, Pageable pageable) {
      Sort sort = pageable.isPaged() ? pageable.getSort() : Sort.unsorted();
      return getQuery(session, spec, entityInformation.getJavaType(), sort);
    }

    private String getCountQueryString() {
      String countQuery = String.format(COUNT_QUERY_STRING, "*", "%s");
      return getQueryString(countQuery, entityInformation.getEntityName());
    }

    protected Mutiny.SelectionQuery<T> getQuery(
        Mutiny.Session session, @Nullable Specification<T> spec, Sort sort) {
      return getQuery(session, spec, entityInformation.getJavaType(), sort);
    }

    protected <S extends T> Mutiny.SelectionQuery<S> getQuery(
        Mutiny.Session session, Specification<S> spec, Class<S> domainClass, Sort sort) {
      CriteriaBuilder builder = sessionFactory.getCriteriaBuilder();
      CriteriaQuery<S> query = builder.createQuery(domainClass);

      Root<S> root = applySpecificationToCriteria(spec, domainClass, query);
      query.select(root);

      if (sort.isSorted()) {
        query.orderBy(toOrders(sort, root, builder));
      }

      return applyRepositoryMethodMetadata(session.createQuery(query));
    }

    private <S> Mutiny.SelectionQuery<S> applyRepositoryMethodMetadata(
        Mutiny.SelectionQuery<S> query) {
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
  }

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
