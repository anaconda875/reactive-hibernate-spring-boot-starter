package com.htech.jpa.reactive;

import static org.hibernate.cfg.TransactionSettings.JTA_PLATFORM;

import com.htech.jpa.pu.CustomPersistenceUnitManager;
import com.htech.jpa.reactive.connection.ReactiveHibernateTransactionManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.transaction.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.hibernate.boot.model.naming.ImplicitNamingStrategy;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.transaction.jta.platform.spi.JtaPlatform;
import org.hibernate.reactive.provider.ReactivePersistenceProvider;
import org.hibernate.reactive.stage.Stage;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.boot.autoconfigure.orm.jpa.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.hibernate5.SpringBeanContainer;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitManager;
import org.springframework.orm.jpa.vendor.AbstractJpaVendorAdapter;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * @author Bao.Ngo
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HibernateProperties.class)
public class ReactiveHibernateJpaConfiguration {

  //  private static final String PROVIDER_DISABLES_AUTOCOMMIT =
  // "hibernate.connection.provider_disables_autocommit";
  private final JpaProperties properties;
  private final BeanFactory beanFactory;
  private final HibernateProperties hibernateProperties;
  private final List<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers;

  public ReactiveHibernateJpaConfiguration(
      JpaProperties properties,
      ConfigurableListableBeanFactory beanFactory,
      HibernateProperties hibernateProperties,
      ObjectProvider<PhysicalNamingStrategy> physicalNamingStrategy,
      ObjectProvider<ImplicitNamingStrategy> implicitNamingStrategy,
      ObjectProvider<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers) {
    this.properties = properties;
    this.beanFactory = beanFactory;
    this.hibernateProperties = hibernateProperties;
    this.hibernatePropertiesCustomizers =
        determineHibernatePropertiesCustomizers(
            physicalNamingStrategy.getIfAvailable(),
            implicitNamingStrategy.getIfAvailable(),
            beanFactory,
            hibernatePropertiesCustomizers.orderedStream().collect(Collectors.toList()));
  }

  private List<HibernatePropertiesCustomizer> determineHibernatePropertiesCustomizers(
      PhysicalNamingStrategy physicalNamingStrategy,
      ImplicitNamingStrategy implicitNamingStrategy,
      ConfigurableListableBeanFactory beanFactory,
      List<HibernatePropertiesCustomizer> hibernatePropertiesCustomizers) {
    List<HibernatePropertiesCustomizer> customizers = new ArrayList<>();
    if (ClassUtils.isPresent(
        "org.hibernate.resource.beans.container.spi.BeanContainer", getClass().getClassLoader())) {
      customizers.add(
          (properties) ->
              properties.put(
                  AvailableSettings.BEAN_CONTAINER, new SpringBeanContainer(beanFactory)));
    }
    if (physicalNamingStrategy != null || implicitNamingStrategy != null) {
      customizers.add(
          new NamingStrategiesHibernatePropertiesCustomizer(
              physicalNamingStrategy, implicitNamingStrategy));
    }
    customizers.addAll(hibernatePropertiesCustomizers);
    return customizers;
  }

  @Bean
  @ConditionalOnMissingBean
  public JpaVendorAdapter jpaVendorAdapter() {
    AbstractJpaVendorAdapter adapter = createJpaVendorAdapter();
    adapter.setShowSql(this.properties.isShowSql());
    if (this.properties.getDatabase() != null) {
      adapter.setDatabase(this.properties.getDatabase());
    }
    if (this.properties.getDatabasePlatform() != null) {
      adapter.setDatabasePlatform(this.properties.getDatabasePlatform());
    }
    adapter.setGenerateDdl(this.properties.isGenerateDdl());
    return adapter;
  }

  @Bean
  @ConditionalOnMissingBean
  public EntityManagerFactoryBuilder entityManagerFactoryBuilder(
      JpaVendorAdapter jpaVendorAdapter,
      ObjectProvider<PersistenceUnitManager> persistenceUnitManager,
      ObjectProvider<EntityManagerFactoryBuilderCustomizer> customizers) {
    Map<String, String> jpaProps = this.properties.getProperties();
    if (org.apache.commons.lang3.StringUtils.isBlank(
        jpaProps.get("jakarta.persistence.jdbc.url"))) {
      throw new EntityManagerFactoryBeanCreationException(
          "Failed to determine a suitable jakarta.persistence.jdbc.url Connection URL", jpaProps);
    }

    EntityManagerFactoryBuilder builder =
        new EntityManagerFactoryBuilder(
            jpaVendorAdapter, jpaProps, persistenceUnitManager.getIfAvailable());
    customizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
    return builder;
  }

  @Bean
  @Primary
  @ConditionalOnMissingBean({
    LocalContainerEntityManagerFactoryBean.class,
    EntityManagerFactory.class
  })
  public LocalContainerEntityManagerFactoryBean entityManagerFactory(
      EntityManagerFactoryBuilder factoryBuilder) {
    Map<String, Object> vendorProperties = getVendorProperties();
    customizeVendorProperties(vendorProperties);
    LocalContainerEntityManagerFactoryBean factoryBean =
        factoryBuilder
            .builder()
            .properties(vendorProperties)
            .mappingResources(getMappingResources())
            .
            /*jta(isJta()).*/ build();
    factoryBean.setPersistenceProviderClass(ReactivePersistenceProvider.class);
    CustomPersistenceUnitManager persistenceUnitManager =
        new CustomPersistenceUnitManager(factoryBean.getJpaPropertyMap());
    String[] packagesToScan = getPackagesToScan();
    persistenceUnitManager.setPackagesToScan(packagesToScan);
    factoryBean.setPersistenceUnitManager(persistenceUnitManager);
    factoryBean.setPackagesToScan(packagesToScan);
    persistenceUnitManager.afterPropertiesSet();

    return factoryBean;
  }

  @Bean
  public Stage.SessionFactory sessionFactory(EntityManagerFactory emf) {
    return emf.unwrap(Stage.SessionFactory.class);
  }

  @Bean
  public org.springframework.transaction.TransactionManager transactionManager(
      Stage.SessionFactory sessionFactory) {
    return new ReactiveHibernateTransactionManager(sessionFactory);
  }

  private String[] getMappingResources() {
    List<String> mappingResources = this.properties.getMappingResources();
    return (!ObjectUtils.isEmpty(mappingResources)
        ? StringUtils.toStringArray(mappingResources)
        : null);
  }

  protected void customizeVendorProperties(Map<String, Object> vendorProperties) {
    if (!vendorProperties.containsKey(JTA_PLATFORM)) {
      configureJtaPlatform(vendorProperties);
    }
    //    if (!vendorProperties.containsKey(PROVIDER_DISABLES_AUTOCOMMIT)) {
    //      configureProviderDisablesAutocommit(vendorProperties);
    //    }
  }

  private void configureJtaPlatform(Map<String, Object> vendorProperties) throws LinkageError {
    JtaTransactionManager jtaTransactionManager = getJtaTransactionManager();
    // Make sure Hibernate doesn't attempt to auto-detect a JTA platform
    if (jtaTransactionManager == null) {
      vendorProperties.put(JTA_PLATFORM, NoJtaPlatform.INSTANCE);
    }
    // As of Hibernate 5.2, Hibernate can fully integrate with the WebSphere
    // transaction manager on its own.
    // Not sure for reactive hibernate
    /*else if (!runningOnWebSphere()) {
      configureSpringJtaPlatform(vendorProperties, jtaTransactionManager);
    }*/
  }

  protected JtaTransactionManager getJtaTransactionManager() {
    // Not sure if reactive hibernate support JtaTransactionManager
    return null;
  }

  protected Map<String, Object> getVendorProperties() {
    Supplier<String> defaultDdlMode = () -> "none";
    return new LinkedHashMap<>(
        this.hibernateProperties.determineHibernateProperties(
            this.properties.getProperties(),
            new HibernateSettings()
                .ddlAuto(defaultDdlMode)
                .hibernatePropertiesCustomizers(this.hibernatePropertiesCustomizers)));
  }

  protected boolean isJta() {
    return false;
  }

  protected String[] getPackagesToScan() {
    List<String> packages = EntityScanPackages.get(this.beanFactory).getPackageNames();
    if (packages.isEmpty() && AutoConfigurationPackages.has(this.beanFactory)) {
      packages = AutoConfigurationPackages.get(this.beanFactory);
    }
    return StringUtils.toStringArray(packages);
  }

  protected AbstractJpaVendorAdapter createJpaVendorAdapter() {
    return new HibernateJpaVendorAdapter();
  }

  private static class NoJtaPlatform implements JtaPlatform {
    static final NoJtaPlatform INSTANCE = new NoJtaPlatform();

    @Override
    public TransactionManager retrieveTransactionManager() {
      return null;
    }

    @Override
    public UserTransaction retrieveUserTransaction() {
      return null;
    }

    @Override
    public Object getTransactionIdentifier(Transaction transaction) {
      return null;
    }

    @Override
    public void registerSynchronization(Synchronization synchronization) {}

    @Override
    public boolean canRegisterSynchronization() {
      return false;
    }

    @Override
    public int getCurrentStatus() throws SystemException {
      return Status.STATUS_UNKNOWN;
    }
  }

  private record NamingStrategiesHibernatePropertiesCustomizer(
      PhysicalNamingStrategy physicalNamingStrategy, ImplicitNamingStrategy implicitNamingStrategy)
      implements HibernatePropertiesCustomizer {

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
      if (this.physicalNamingStrategy != null) {
        hibernateProperties.put("hibernate.physical_naming_strategy", this.physicalNamingStrategy);
      }
      if (this.implicitNamingStrategy != null) {
        hibernateProperties.put("hibernate.implicit_naming_strategy", this.implicitNamingStrategy);
      }
    }
  }

  public static class EntityManagerFactoryBeanCreationException extends BeanCreationException {

    private final Map<String, String> jpaProps;

    public EntityManagerFactoryBeanCreationException(String msg, Map<String, String> jpaProps) {
      super(msg);
      this.jpaProps = jpaProps;
    }

    public Map<String, String> getJpaProps() {
      return jpaProps;
    }
  }
}
