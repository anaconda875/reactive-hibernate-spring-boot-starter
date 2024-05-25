package com.htech.data.jpa.reactive.core;

import org.hibernate.reactive.stage.Stage;

public interface ReactiveJpaEntityOperations {

  Stage.SessionFactory getSessionFactory();
}
