package com.htech.data.jpa.reactive.config;

import com.htech.data.jpa.reactive.mapping.event.ReactiveAuditingEntityCallback;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.data.auditing.ReactiveIsNewAwareAuditingHandler;
import org.springframework.data.auditing.config.AuditingBeanDefinitionRegistrarSupport;
import org.springframework.data.auditing.config.AuditingConfiguration;
import org.springframework.data.config.ParsingUtils;
import org.springframework.data.jpa.repository.config.JpaMetamodelMappingContextFactoryBean;
import org.springframework.util.Assert;

import java.lang.annotation.Annotation;

class ReactiveJpaAuditingRegistrar extends AuditingBeanDefinitionRegistrarSupport {

  private static final String JPA_MAPPING_CONTEXT_BEAN_NAME = "jpaMappingContext";

  @Override
  protected Class<? extends Annotation> getAnnotation() {
    return EnableReactiveJpaAuditing.class;
  }

  @Override
  protected String getAuditingHandlerBeanName() {
    return "reactiveJpaAuditingHandler";
  }

  @Override
  protected void postProcess(BeanDefinitionBuilder builder, AuditingConfiguration configuration,
                             BeanDefinitionRegistry registry) {
    builder.setFactoryMethod("from").addConstructorArgReference(JPA_MAPPING_CONTEXT_BEAN_NAME);
  }

  @Override
  protected BeanDefinitionBuilder getAuditHandlerBeanDefinitionBuilder(AuditingConfiguration configuration) {
    Assert.notNull(configuration, "AuditingConfiguration must not be null");

    return configureDefaultAuditHandlerAttributes(configuration,
        BeanDefinitionBuilder.rootBeanDefinition(ReactiveIsNewAwareAuditingHandler.class));
  }

  @Override
  protected void registerAuditListenerBeanDefinition(BeanDefinition auditingHandlerDefinition,
                                                     BeanDefinitionRegistry registry) {
    Assert.notNull(auditingHandlerDefinition, "BeanDefinition must not be null");
    Assert.notNull(registry, "BeanDefinitionRegistry must not be null");

    if (!registry.containsBeanDefinition(JPA_MAPPING_CONTEXT_BEAN_NAME)) {
      registry.registerBeanDefinition(JPA_MAPPING_CONTEXT_BEAN_NAME, //
          new RootBeanDefinition(JpaMetamodelMappingContextFactoryBean.class));
    }

    BeanDefinitionBuilder listenerBeanDefinitionBuilder = BeanDefinitionBuilder
        .rootBeanDefinition(ReactiveAuditingEntityCallback.class);
    listenerBeanDefinitionBuilder
        .addConstructorArgValue(ParsingUtils.getObjectFactoryBeanDefinition(getAuditingHandlerBeanName(), registry));

    registerInfrastructureBeanWithId(listenerBeanDefinitionBuilder.getBeanDefinition(),
        ReactiveAuditingEntityCallback.class.getName(), registry);
  }
}

