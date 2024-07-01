package com.htech.jpa.reactive;

/**
 * @author Bao.Ngo
 */
@FunctionalInterface
public interface EntityManagerFactoryBuilderCustomizer {

  void customize(EntityManagerFactoryBuilder builder);
}
