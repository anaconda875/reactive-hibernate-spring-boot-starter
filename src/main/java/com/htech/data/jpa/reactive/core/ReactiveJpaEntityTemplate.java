package com.htech.data.jpa.reactive.core;

import org.hibernate.reactive.stage.Stage;

public class ReactiveJpaEntityTemplate implements ReactiveJpaEntityOperations {

  protected final Stage.SessionFactory sessionFactory;

  public ReactiveJpaEntityTemplate(Stage.SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public Stage.SessionFactory getSessionFactory() {
    return sessionFactory;
  }
}
