package com.htech.data.jpa.reactive.repository.support;

import com.htech.data.jpa.reactive.core.StageReactiveJpaEntityOperations;
import com.htech.data.jpa.reactive.repository.query.ReactiveJpaQueryLookupStrategy;
import com.htech.data.jpa.reactive.repository.query.ReactiveJpaQueryMethodFactory;
import com.htech.data.jpa.reactive.repository.query.ReactiveQueryRewriterProvider;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Metamodel;
import java.util.Optional;
import org.hibernate.reactive.stage.Stage;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.data.jpa.repository.support.JpaMetamodelEntityInformation;
import org.springframework.data.jpa.repository.support.JpaPersistableEntityInformation;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.ReactiveRepositoryFactorySupport;
import org.springframework.data.repository.query.*;
import org.springframework.lang.Nullable;

/**
 * @author Bao.Ngo
 */
public class ReactiveJpaRepositoryFactory extends ReactiveRepositoryFactorySupport
    implements BeanClassLoaderAware {

  protected final StageReactiveJpaEntityOperations entityOperations;
  protected final Stage.SessionFactory sessionFactory;
  protected final EntityManagerFactory entityManagerFactory;
  protected ClassLoader classLoader;
  protected EscapeCharacter escapeCharacter;

  protected ReactiveJpaQueryMethodFactory queryMethodFactory;
  protected ReactiveQueryRewriterProvider queryRewriterProvider;

  public ReactiveJpaRepositoryFactory(
      StageReactiveJpaEntityOperations entityOperations,
      Stage.SessionFactory sessionFactory,
      EntityManagerFactory entityManagerFactory) {
    this.entityOperations = entityOperations;
    this.sessionFactory = sessionFactory;
    this.entityManagerFactory = entityManagerFactory;
  }

  @Override
  @SuppressWarnings({"rawtypes"})
  public <T, ID> EntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
    Metamodel metamodel = sessionFactory.getMetamodel();
    if (Persistable.class.isAssignableFrom(domainClass)) {
      return new JpaPersistableEntityInformation(
          domainClass, metamodel, entityManagerFactory.getPersistenceUnitUtil());
    }

    return new JpaMetamodelEntityInformation(
        domainClass, metamodel, entityManagerFactory.getPersistenceUnitUtil());
  }

  @Override
  protected Object getTargetRepository(RepositoryInformation repositoryInformation) {
    EntityInformation<?, Object> entityInformation =
        getEntityInformation(repositoryInformation.getDomainType());
    ReactiveJpaRepositoryImplementation<?, ?> repository =
        getTargetRepositoryViaReflection(
            repositoryInformation, entityInformation, sessionFactory, entityOperations);

    // repository.setRepositoryMethodMetadata(crudMethodMetadataPostProcessor.getCrudMethodMetadata());
    repository.setEscapeCharacter(escapeCharacter);

    return repository;
  }

  @Override
  protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
    return SimpleReactiveJpaRepository.class;
  }

  public void setEscapeCharacter(EscapeCharacter escapeCharacter) {
    this.escapeCharacter = escapeCharacter;
  }

  @Override
  protected Optional<QueryLookupStrategy> getQueryLookupStrategy(
      @Nullable QueryLookupStrategy.Key key, ValueExpressionDelegate valueExpressionDelegate) {
    return Optional.of(
        ReactiveJpaQueryLookupStrategy.create(
            entityManagerFactory,
            sessionFactory,
            queryMethodFactory,
            key,
            new CachingValueExpressionDelegate(valueExpressionDelegate),
            queryRewriterProvider,
            escapeCharacter));
  }

  @Override
  public void setBeanClassLoader(ClassLoader classLoader) {
    super.setBeanClassLoader(classLoader);
    this.classLoader = classLoader;
  }

  @Override
  public RepositoryMetadata getRepositoryMetadata(Class<?> repositoryInterface) {
    return super.getRepositoryMetadata(repositoryInterface);
  }

  public ReactiveJpaQueryMethodFactory getQueryMethodFactory() {
    return queryMethodFactory;
  }

  public void setQueryMethodFactory(ReactiveJpaQueryMethodFactory queryMethodFactory) {
    this.queryMethodFactory = queryMethodFactory;
  }

  public ReactiveQueryRewriterProvider getQueryRewriterProvider() {
    return queryRewriterProvider;
  }

  public void setQueryRewriterProvider(ReactiveQueryRewriterProvider queryRewriterProvider) {
    this.queryRewriterProvider = queryRewriterProvider;
  }
}
