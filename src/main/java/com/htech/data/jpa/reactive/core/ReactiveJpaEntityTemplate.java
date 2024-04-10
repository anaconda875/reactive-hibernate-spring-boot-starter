package com.htech.data.jpa.reactive.core;

import org.hibernate.reactive.mutiny.Mutiny;

public class ReactiveJpaEntityTemplate implements ReactiveJpaEntityOperations {

  protected final Mutiny.SessionFactory sessionFactory;

  public ReactiveJpaEntityTemplate(Mutiny.SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public Mutiny.SessionFactory getSessionFactory() {
    return sessionFactory;
  }
}
