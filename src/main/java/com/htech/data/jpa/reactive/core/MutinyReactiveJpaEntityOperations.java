package com.htech.data.jpa.reactive.core;

import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

public interface MutinyReactiveJpaEntityOperations {

  <T> Uni<T> persist(T entity, Mutiny.Session session, Mutiny.Transaction transaction);

  Mutiny.SessionFactory sessionFactory();

}
