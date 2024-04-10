package com.htech.jpa.reactive;

import org.hibernate.reactive.provider.ReactivePersistenceProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ LocalContainerEntityManagerFactoryBean.class, ReactivePersistenceProvider.class })
@EnableConfigurationProperties(JpaProperties.class)
@Import(ReactiveHibernateJpaConfiguration.class)
public class ReactiveHibernateJpaAutoConfiguration {
}
