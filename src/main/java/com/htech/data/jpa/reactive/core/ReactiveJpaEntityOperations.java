package com.htech.data.jpa.reactive.core;

import org.hibernate.reactive.mutiny.Mutiny;

public interface ReactiveJpaEntityOperations {

  Mutiny.SessionFactory getSessionFactory();
}
