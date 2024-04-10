package com.htech.jpa.reactive.repository.config;

import com.htech.jpa.reactive.repository.support.ReactiveJpaRepositoryFactoryBean;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.JpaMetamodelMappingContextFactoryBean;
import org.springframework.data.jpa.repository.support.JpaEvaluationContextExtension;
import org.springframework.data.repository.config.AnnotationRepositoryConfigurationSource;
import org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport;
import org.springframework.data.repository.config.RepositoryConfigurationSource;
import org.springframework.data.repository.core.RepositoryMetadata;

import java.lang.annotation.Annotation;
import java.util.*;


public class ReactiveJpaRepositoryConfigExtension extends RepositoryConfigurationExtensionSupport {

  private static final String JPA_METAMODEL_CACHE_CLEANUP_CLASSNAME = "org.springframework.data.jpa.util.JpaMetamodelCacheCleanup";
  private static final String JPA_MAPPING_CONTEXT_BEAN_NAME = "jpaMappingContext";
  private static final String ESCAPE_CHARACTER_PROPERTY = "escapeCharacter";

  @Override
  public String getModuleName() {
    return "REACTIVE_JPA";
  }

  @Override
  public String getRepositoryFactoryBeanClassName() {
    return ReactiveJpaRepositoryFactoryBean.class.getName();
  }

  @Override
  protected String getModulePrefix() {
    return getModuleName().toLowerCase(Locale.US);
  }

  @Override
  protected Collection<Class<? extends Annotation>> getIdentifyingAnnotations() {
    return Arrays.asList(Entity.class, MappedSuperclass.class);
  }

  @Override
  protected Collection<Class<?>> getIdentifyingTypes() {
    return Collections.<Class<?>> singleton(JpaRepository.class);
  }

  @Override
  public void postProcess(BeanDefinitionBuilder builder, AnnotationRepositoryConfigurationSource config) {
    AnnotationAttributes attributes = config.getAttributes();

    String reactiveJpaEntityOperationsRef = attributes.getString("reactiveJpaEntityOperationsRef");
//    if (StringUtils.hasText(reactiveJpaEntityOperationsRef)) {
    builder.addPropertyReference("entityOperations", reactiveJpaEntityOperationsRef);
    builder.addPropertyValue(ESCAPE_CHARACTER_PROPERTY, getEscapeCharacter(config).orElse('\\'));
//    } else {
//      //TODO
//    }
  }

  private static Optional<Character> getEscapeCharacter(RepositoryConfigurationSource source) {
    try {
      return source.getAttribute(ESCAPE_CHARACTER_PROPERTY, Character.class);
    } catch (IllegalArgumentException ___) {
      return Optional.empty();
    }
  }

  @Override
  public void registerBeansForRoot(BeanDefinitionRegistry registry, RepositoryConfigurationSource config) {

    super.registerBeansForRoot(registry, config);

//    registerSharedEntityMangerIfNotAlreadyRegistered(registry, config);

    Object source = config.getSource();

//    registerLazyIfNotAlreadyRegistered(
//        () -> new RootBeanDefinition(EntityManagerBeanDefinitionRegistrarPostProcessor.class), registry,
//        EM_BEAN_DEFINITION_REGISTRAR_POST_PROCESSOR_BEAN_NAME, source);

    registerLazyIfNotAlreadyRegistered(() -> new RootBeanDefinition(JpaMetamodelMappingContextFactoryBean.class),
        registry, JPA_MAPPING_CONTEXT_BEAN_NAME, source);

//    registerLazyIfNotAlreadyRegistered(() -> new RootBeanDefinition(PAB_POST_PROCESSOR), registry,
//        AnnotationConfigUtils.PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME, source);

    // Register bean definition for DefaultJpaContext

//    registerLazyIfNotAlreadyRegistered(() -> {
//
//      RootBeanDefinition contextDefinition = new RootBeanDefinition(DefaultJpaContext.class);
//      contextDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
//
//      return contextDefinition;
//
//    }, registry, JPA_CONTEXT_BEAN_NAME, source);

    registerIfNotAlreadyRegistered(() -> new RootBeanDefinition(JPA_METAMODEL_CACHE_CLEANUP_CLASSNAME), registry,
        JPA_METAMODEL_CACHE_CLEANUP_CLASSNAME, source);

    // EvaluationContextExtension for JPA specific SpEL functions

    registerIfNotAlreadyRegistered(() -> {

      Object value = AnnotationRepositoryConfigurationSource.class.isInstance(config) //
          ? config.getRequiredAttribute(ESCAPE_CHARACTER_PROPERTY, Character.class) //
          : config.getAttribute(ESCAPE_CHARACTER_PROPERTY).orElse("\\");

      BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(JpaEvaluationContextExtension.class);
      builder.addConstructorArgValue(value);

      return builder.getBeanDefinition();

    }, registry, JpaEvaluationContextExtension.class.getName(), source);
  }

  @Override
  protected boolean useRepositoryConfiguration(RepositoryMetadata metadata) {
    return metadata.isReactiveRepository();
  }
}
