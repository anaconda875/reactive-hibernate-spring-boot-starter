package com.htech.data.jpa.reactive.repository.support;

import static org.springframework.transaction.reactive.TransactionSynchronizationManager.forCurrentTransaction;

import com.htech.jpa.reactive.connection.ConnectionHolder;
import com.htech.jpa.reactive.connection.SessionContextHolder;
import com.htech.jpa.reactive.connection.TransactionUtils;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.reactive.stage.Stage;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SessionPostProcessor implements RepositoryProxyPostProcessor {

  private final Stage.SessionFactory sessionFactory;

  public SessionPostProcessor(Stage.SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public void postProcess(ProxyFactory factory, RepositoryInformation repositoryInformation) {
    factory.addAdvice(new SessionInterceptor(sessionFactory));
  }

  static class SessionInterceptor implements MethodInterceptor {

    private final Stage.SessionFactory sessionFactory;

    public SessionInterceptor(Stage.SessionFactory sessionFactory) {
      this.sessionFactory = sessionFactory;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
      Object proceed = invocation.proceed();
      Mono<Stage.Session> session = currentSession(sessionFactory);
      Mono<Boolean> transactionAvailable = TransactionUtils.isTransactionAvailable(sessionFactory);
      if (proceed instanceof Mono<?> mono) {
        return Mono.usingWhen(
            Mono.just("dummy"),
            str -> mono.contextWrite(SessionContextHolder.set(session)),
            str -> closeNormally(transactionAvailable, session),
            (str, t) -> closeExceptionally(t, transactionAvailable, session),
            str -> closeNormally(transactionAvailable, session));
      } else if (proceed instanceof Flux<?> flux) {
        return Flux.usingWhen(
            Mono.just("dummy"),
            str -> flux.contextWrite(SessionContextHolder.set(session)),
            str -> closeNormally(transactionAvailable, session),
            (str, t) -> closeExceptionally(t, transactionAvailable, session),
            str -> closeNormally(transactionAvailable, session));
      }

      return proceed;
    }

    private static Mono<Stage.Session> currentSession(Stage.SessionFactory sessionFactory) {
      return forCurrentTransaction()
          .mapNotNull(tsm -> tsm.getResource(sessionFactory))
          .filter(ConnectionHolder.class::isInstance)
          .onErrorResume(e -> Mono.empty())
          .map(ConnectionHolder.class::cast)
          .map(ConnectionHolder::getConnection)
          .map(Stage.Session.class::cast)
          .switchIfEmpty(Mono.defer(() -> Mono.fromCompletionStage(sessionFactory.openSession())))
          .cache();
    }

    private static Mono<Object> closeExceptionally(
        Throwable t, Mono<Boolean> transactionAvailable, Mono<Stage.Session> session) {
      return transactionAvailable.flatMap(
          b -> {
            if (b) {
              return Mono.error(t);
            }
            return session
                .flatMap(s -> Mono.defer(() -> Mono.fromCompletionStage(s.close())))
                .then(Mono.error(t));
          });
    }

    private static Mono<Void> closeNormally(
        Mono<Boolean> transactionAvailable, Mono<Stage.Session> session) {
      return transactionAvailable.flatMap(
          b -> {
            if (b) {
              return Mono.empty();
            }
            return session.flatMap(s -> Mono.defer(() -> Mono.fromCompletionStage(s.close())));
          });
    }
  }
}
