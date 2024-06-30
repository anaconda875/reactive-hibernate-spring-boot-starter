package com.htech.data.jpa.reactive.repository.support;

import jakarta.persistence.NoResultException;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.reactive.stage.Stage;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PersistenceExceptionHandlerPostProcessor implements RepositoryProxyPostProcessor {

  private final Stage.SessionFactory sessionFactory;

  public PersistenceExceptionHandlerPostProcessor(Stage.SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public void postProcess(ProxyFactory factory, RepositoryInformation repositoryInformation) {
    factory.addAdvice(new PersistenceExceptionHandlerInterceptor(sessionFactory));
  }

  static class PersistenceExceptionHandlerInterceptor implements MethodInterceptor {

    private final Stage.SessionFactory sessionFactory;

    public PersistenceExceptionHandlerInterceptor(Stage.SessionFactory sessionFactory) {
      this.sessionFactory = sessionFactory;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
      Object proceed = invocation.proceed();
      if (proceed instanceof Mono<?> mono) {
        return mono.onErrorResume(NoResultException.class, e -> Mono.empty());
      } else if (proceed instanceof Flux<?> flux) {
        return flux.onErrorResume(NoResultException.class, e -> Mono.empty());
      }

      return proceed;
    }
  }
}
