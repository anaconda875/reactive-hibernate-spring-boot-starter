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

/**
 * @author Bao.Ngo
 */
public class SessionAwarePostProcessor implements RepositoryProxyPostProcessor {

  private final Stage.SessionFactory sessionFactory;

  public SessionAwarePostProcessor(Stage.SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public void postProcess(ProxyFactory factory, RepositoryInformation repositoryInformation) {
    factory.addAdvice(new SessionAwareInterceptor(sessionFactory));
  }

  static class SessionAwareInterceptor implements MethodInterceptor {

    private final Stage.SessionFactory sessionFactory;

    public SessionAwareInterceptor(Stage.SessionFactory sessionFactory) {
      this.sessionFactory = sessionFactory;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
      Object proceed = invocation.proceed();
      Mono<Stage.Session> session = currentSession(sessionFactory);
      if (proceed instanceof Mono<?> mono) {
        return Mono.usingWhen(
            TransactionUtils.isTransactionAvailable(sessionFactory),
            transactionAvailable -> mono.contextWrite(SessionContextHolder.set(session)),
            transactionAvailable -> closeNormally(transactionAvailable, session),
            (transactionAvailable, t) -> closeExceptionally(t, transactionAvailable, session),
            transactionAvailable -> closeNormally(transactionAvailable, session));
      } else if (proceed instanceof Flux<?> flux) {
        return Flux.usingWhen(
            TransactionUtils.isTransactionAvailable(sessionFactory),
            transactionAvailable -> flux.contextWrite(SessionContextHolder.set(session)),
            transactionAvailable -> closeNormally(transactionAvailable, session),
            (transactionAvailable, t) -> closeExceptionally(t, transactionAvailable, session),
            transactionAvailable -> closeNormally(transactionAvailable, session));
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
        Throwable t, Boolean transactionAvailable, Mono<Stage.Session> session) {
      if (transactionAvailable) {
        return Mono.error(t);
      }

      return session
          .flatMap(
              s ->
                  s.isOpen()
                      ? Mono.defer(() -> Mono.fromCompletionStage(s.close()))
                      : Mono.error(t))
          .then(Mono.error(t));
    }

    private static Mono<Void> closeNormally(
        Boolean transactionAvailable, Mono<Stage.Session> session) {
      if (transactionAvailable) {
        return Mono.empty();
      }

      return session.flatMap(
          s -> s.isOpen() ? Mono.defer(() -> Mono.fromCompletionStage(s.close())) : Mono.empty());
    }
  }
}
