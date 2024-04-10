package com.htech.jpa.reactive;

@FunctionalInterface
public interface EntityManagerFactoryBuilderCustomizer {

  /**
   * Customize the given {@code builder}.
   * @param builder the builder to customize
   */
  void customize(EntityManagerFactoryBuilder builder);

}
