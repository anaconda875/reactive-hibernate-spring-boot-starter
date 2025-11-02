package com.htech.data.jpa.reactive.repository.support;

import com.htech.data.jpa.reactive.core.StageReactiveJpaEntityOperations;
import com.htech.data.jpa.reactive.repository.query.DefaultReactiveJpaQueryExtractor;
import com.htech.data.jpa.reactive.repository.query.ReactiveJpaQueryMethodFactory;
import com.htech.data.jpa.reactive.repository.query.ReactiveQueryRewriterProvider;
import jakarta.persistence.EntityManagerFactory;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.querydsl.EntityPathResolver;
import org.springframework.data.querydsl.SimpleEntityPathResolver;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.TransactionalRepositoryFactoryBeanSupport;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.ReactiveExtensionAwareQueryMethodEvaluationContextProvider;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;

/**
 * @author Bao.Ngo
 */
public class ReactiveJpaRepositoryFactoryBean<
        T extends Repository<S, ID>, S, ID extends Serializable>
    extends TransactionalRepositoryFactoryBeanSupport<T, S, ID>
    implements ApplicationContextAware, BeanClassLoaderAware {

  private @Nullable ApplicationContext applicationContext;
  private StageReactiveJpaEntityOperations entityOperations;

  private EntityPathResolver entityPathResolver;

  private EscapeCharacter escapeCharacter = EscapeCharacter.DEFAULT;

  protected ReactiveJpaRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
    super(repositoryInterface);
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  @Override
  protected RepositoryFactorySupport doCreateRepositoryFactory() {
    ReactiveJpaRepositoryFactory factory =
        new ReactiveJpaRepositoryFactory(
            entityOperations,
            entityOperations.sessionFactory(),
            applicationContext.getBean("entityManagerFactory", EntityManagerFactory.class));
    factory.setEscapeCharacter(escapeCharacter);
    // TODO
    factory.setQueryMethodFactory(
        new ReactiveJpaQueryMethodFactory(new DefaultReactiveJpaQueryExtractor()));
    factory.setQueryRewriterProvider(ReactiveQueryRewriterProvider.simple());

    /*RepositoryMetadata repositoryMetadata = factory.getRepositoryMetadata(getObjectType());
    factory.addRepositoryProxyPostProcessor(new ValueAdapterInterceptorProxyPostProcessor());
    factory.addRepositoryProxyPostProcessor(new SessionAwareProxyPostProcessor());*/
    factory.addRepositoryProxyPostProcessor(new CrudMethodMetadataPostProcessor());
    factory.addRepositoryProxyPostProcessor(
        new SessionAwarePostProcessor(entityOperations.sessionFactory()));
    factory.addRepositoryProxyPostProcessor((f, repositoryInformation) -> {
      if (isTransactionNeeded(repositoryInformation.getRepositoryInterface())) {
        f.addAdvice(applicationContext.getBean(SurroundingTransactionDetectorMethodInterceptor.class));
      }
    });
    factory.addRepositoryProxyPostProcessor(
        new PersistenceExceptionHandlerPostProcessor(entityOperations.sessionFactory()));

    return factory;
  }

  @Override
  protected Optional<QueryMethodEvaluationContextProvider>
      createDefaultQueryMethodEvaluationContextProvider(ListableBeanFactory beanFactory) {
    return Optional.of(new ReactiveExtensionAwareQueryMethodEvaluationContextProvider(beanFactory));
  }

  //  @Autowired
  public void setEntityOperations(@Nullable StageReactiveJpaEntityOperations entityOperations) {
    this.entityOperations = entityOperations;
  }

  @Autowired
  public void setEntityPathResolver(ObjectProvider<EntityPathResolver> resolver) {
    this.entityPathResolver = resolver.getIfAvailable(() -> SimpleEntityPathResolver.INSTANCE);
  }

  public void setEscapeCharacter(char escapeCharacter) {
    this.escapeCharacter = EscapeCharacter.of(escapeCharacter);
  }


  private static boolean isTransactionNeeded(Class<?> repositoryClass) {
    Method[] methods = ReflectionUtils.getAllDeclaredMethods(repositoryClass);

    for (Method method : methods) {
      if (Stream.class.isAssignableFrom(method.getReturnType()) || method.isAnnotationPresent(Procedure.class)) {
        return true;
      }
    }

    return false;
  }
}
