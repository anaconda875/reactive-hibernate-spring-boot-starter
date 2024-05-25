package com.htech.data.jpa.reactive.repository.query;

import org.hibernate.reactive.stage.Stage;
import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import org.springframework.data.repository.query.Parameters;

public class ReactiveJpaParametersParameterAccessor extends JpaParametersParameterAccessor {

  protected final Stage.SessionFactory sessionFactory;

  //  protected final Mono<Stage.Session> session;
  //  protected final Stage.Session session;
  //  protected final Stage.Transaction transaction;

  public ReactiveJpaParametersParameterAccessor(
      Parameters<?, ?> parameters, Object[] values, Stage.SessionFactory sessionFactory /*,
      Mono<Stage.Session> session,
      Stage.Session session,
      Stage.Transaction transaction*/) {
    super(parameters, values);
    this.sessionFactory = sessionFactory;
    //    this.session = session;
    //    this.transaction = transaction;
  }

  public Stage.SessionFactory getSessionFactory() {
    return sessionFactory;
  }

  //  public Mono<Stage.Session> getSession() {
  //    return session;
  //  }

  //  public Stage.Transaction getTransaction() {
  //    return transaction;
  //  }
}
