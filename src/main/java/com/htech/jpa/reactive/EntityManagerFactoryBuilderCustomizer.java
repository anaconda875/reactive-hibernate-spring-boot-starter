package com.htech.jpa.reactive;

@FunctionalInterface
public interface EntityManagerFactoryBuilderCustomizer {

  void customize(EntityManagerFactoryBuilder builder);
}
