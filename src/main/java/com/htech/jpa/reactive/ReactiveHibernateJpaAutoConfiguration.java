package com.htech.jpa.reactive;

import org.hibernate.reactive.provider.ReactivePersistenceProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * @author Bao.Ngo
 */
@Configuration(proxyBeanMethods = false)
@AutoConfiguration(before = TransactionAutoConfiguration.class)
@ConditionalOnClass({
  LocalContainerEntityManagerFactoryBean.class,
  ReactivePersistenceProvider.class
})
@EnableConfigurationProperties(JpaProperties.class)
@Import(ReactiveHibernateJpaConfiguration.class)
public class ReactiveHibernateJpaAutoConfiguration {}
