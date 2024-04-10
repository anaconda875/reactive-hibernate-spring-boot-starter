package com.htech.jpa.reactive.repository.query;

import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import org.springframework.data.repository.query.Parameters;

public class ReactiveJpaParametersParameterAccessor extends JpaParametersParameterAccessor {

  protected final Mutiny.SessionFactory sessionFactory;
  protected final Mutiny.Session session;
  protected final Mutiny.Transaction transaction;

  public ReactiveJpaParametersParameterAccessor(Parameters<?, ?> parameters, Object[] values, Mutiny.SessionFactory sessionFactory, Mutiny.Session session, Mutiny.Transaction transaction) {
    super(parameters, values);
    this.sessionFactory = sessionFactory;
    this.session = session;
    this.transaction = transaction;
  }

  public Mutiny.SessionFactory getSessionFactory() {
    return sessionFactory;
  }

  public Mutiny.Session getSession() {
    return session;
  }

  public Mutiny.Transaction getTransaction() {
    return transaction;
  }
}
