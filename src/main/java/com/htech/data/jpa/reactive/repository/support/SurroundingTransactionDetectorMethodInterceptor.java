package com.htech.data.jpa.reactive.repository.support;

import com.htech.jpa.reactive.connection.TransactionUtils;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.reactive.stage.Stage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SurroundingTransactionDetectorMethodInterceptor implements MethodInterceptor {

  private final Stage.SessionFactory sessionFactory;

  public SurroundingTransactionDetectorMethodInterceptor(Stage.SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    Object proceed = invocation.proceed();
    if (proceed instanceof Mono<?> mono) {
      return mono.contextWrite(TransactionUtils.setTransactionState(sessionFactory));
    }

    if (proceed instanceof Flux<?> flux) {
      return flux.contextWrite(TransactionUtils.setTransactionState(sessionFactory));
    }

    return proceed;
  }

}
