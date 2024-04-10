package com.htech.jpa.reactive.repository.support;

import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@SuppressWarnings("unchecked")
public class SimpleReactiveJpaRepository<T, ID> implements ReactiveJpaRepositoryImplementation<T, ID> {

  private final JpaEntityInformation<T, ?> entityInformation;
  private final Mutiny.SessionFactory sessionFactory;

  private final ClassLoader classLoader;

  private final InternalRepository delegating;

  public static <T,ID> ReactiveJpaRepositoryImplementation<T, ID> createInstance(JpaEntityInformation<T, ?> entityInformation, ClassLoader classLoader) {
    SimpleReactiveJpaRepository<T,ID> instance = new SimpleReactiveJpaRepository<>(entityInformation, classLoader);
    ProxyFactory proxyFactory = new ProxyFactory(instance.delegating);
//    proxyFactory.setInterfaces(SessionAwareReactiveJpaRepositoryImplementation.class);
//    proxyFactory.

    return (ReactiveJpaRepositoryImplementation<T, ID>) proxyFactory.getProxy(classLoader);
  }

  private SimpleReactiveJpaRepository(JpaEntityInformation<T, ?> entityInformation/*, Mutiny.SessionFactory sessionFactory*/, ClassLoader classLoader) {
    this.sessionFactory = null;
    this.entityInformation = entityInformation;
    this.classLoader = classLoader;
    delegating = new InternalRepository();
  }

  private SimpleReactiveJpaRepository() {
    entityInformation = null;
    sessionFactory = null;
    classLoader = null;
    delegating = null;
  }

//  @Override
//  public Uni<T> findById(ID id) {
//    return sessionFactory.withSession(session -> session.find(entityInformation.getJavaType(), id));
//  }

  @Override
  public Flux<T> findAll() {
    return Flux.empty();
  }

  @Override
  public Mono<T> findById(ID id) {
//    return toWrapper(sessionFactory.withSession(session -> session.find(entityInformation.getJavaType(), id)), Mono.class);
    return Mono.empty();
  }

  @Override
  public Mono<T> save(T entity) {
    return Mono.empty();
  }

  interface SessionAwareReactiveJpaRepositoryImplementation<T, ID> extends ReactiveJpaRepositoryImplementation<T, ID> {

    Uni<T> findById(ID id, Mutiny.Session session, @Nullable Mutiny.Transaction transaction);

    Uni<T> save(T entity, Mutiny.Session session, @Nullable Mutiny.Transaction transaction);

  }

  class InternalRepository extends SimpleReactiveJpaRepository<T, ID> implements SessionAwareReactiveJpaRepositoryImplementation<T, ID> {

    @Override
    public Uni<T> findById(ID id, Mutiny.Session session, @Nullable Mutiny.Transaction transaction) {
//      return toWrapper(session.find(SimpleReactiveJpaRepository.this.entityInformation.getJavaType(), id), Mono.class);
      return session.find(SimpleReactiveJpaRepository.this.entityInformation.getJavaType(), id);
    }

    @Override
    public Uni<T> save(T entity, Mutiny.Session session, Mutiny.Transaction transaction) {
      return session.persist(entity).chain(session::flush).replaceWith(entity);
//      return Uni.createFrom().item(entity);
    }
  }
}
