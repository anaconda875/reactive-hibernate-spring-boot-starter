package com.htech.data.jpa.reactive.core;

import com.htech.jpa.reactive.ReactiveHibernateJpaAutoConfiguration;
import org.hibernate.reactive.stage.Stage;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({Stage.SessionFactory.class})
@AutoConfigureAfter(ReactiveHibernateJpaAutoConfiguration.class)
public class ReactiveJpaDataAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public StageReactiveJpaEntityOperations reactiveJpaEntityTemplate(
      Stage.SessionFactory sessionFactory) {
    return new StageReactiveJpaEntityTemplate(sessionFactory);
  }
}
