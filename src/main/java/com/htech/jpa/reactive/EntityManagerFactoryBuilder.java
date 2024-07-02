package com.htech.jpa.reactive;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitManager;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitPostProcessor;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * @author Bao.Ngo
 */
public class EntityManagerFactoryBuilder {

  private final JpaVendorAdapter jpaVendorAdapter;

  private final PersistenceUnitManager persistenceUnitManager;

  private final Map<String, Object> jpaProperties;

  private final URL persistenceUnitRootLocation;

  private AsyncTaskExecutor bootstrapExecutor;

  private PersistenceUnitPostProcessor[] persistenceUnitPostProcessors;

  public EntityManagerFactoryBuilder(
      JpaVendorAdapter jpaVendorAdapter,
      Map<String, ?> jpaProperties,
      PersistenceUnitManager persistenceUnitManager) {
    this(jpaVendorAdapter, jpaProperties, persistenceUnitManager, null);
  }

  public EntityManagerFactoryBuilder(
      JpaVendorAdapter jpaVendorAdapter,
      Map<String, ?> jpaProperties,
      PersistenceUnitManager persistenceUnitManager,
      URL persistenceUnitRootLocation) {
    this.jpaVendorAdapter = jpaVendorAdapter;
    this.persistenceUnitManager = persistenceUnitManager;
    this.jpaProperties = new LinkedHashMap<>(jpaProperties);
    this.persistenceUnitRootLocation = persistenceUnitRootLocation;
  }

  public Builder builder() {
    return new Builder();
  }

  public void setBootstrapExecutor(AsyncTaskExecutor bootstrapExecutor) {
    this.bootstrapExecutor = bootstrapExecutor;
  }

  public void setPersistenceUnitPostProcessors(
      PersistenceUnitPostProcessor... persistenceUnitPostProcessors) {
    this.persistenceUnitPostProcessors = persistenceUnitPostProcessors;
  }

  public class Builder {

    private PersistenceManagedTypes managedTypes;

    private String[] packagesToScan;

    private String persistenceUnit;

    private Map<String, Object> properties = new HashMap<>();

    private String[] mappingResources;

    protected Builder() {}

    public Builder managedTypes(PersistenceManagedTypes managedTypes) {
      this.managedTypes = managedTypes;
      return this;
    }

    public Builder packages(String... packagesToScan) {
      this.packagesToScan = packagesToScan;
      return this;
    }

    public Builder packages(Class<?>... basePackageClasses) {
      Set<String> packages = new HashSet<>();
      for (Class<?> type : basePackageClasses) {
        packages.add(ClassUtils.getPackageName(type));
      }
      this.packagesToScan = StringUtils.toStringArray(packages);
      return this;
    }

    public Builder persistenceUnit(String persistenceUnit) {
      this.persistenceUnit = persistenceUnit;
      return this;
    }

    public Builder properties(Map<String, ?> properties) {
      this.properties.putAll(properties);
      return this;
    }

    public Builder mappingResources(String... mappingResources) {
      this.mappingResources = mappingResources;
      return this;
    }

    public LocalContainerEntityManagerFactoryBean build() {
      LocalContainerEntityManagerFactoryBean entityManagerFactoryBean =
          new LocalContainerEntityManagerFactoryBean();
      if (persistenceUnitManager != null) {
        entityManagerFactoryBean.setPersistenceUnitManager(persistenceUnitManager);
      }
      if (this.persistenceUnit != null) {
        entityManagerFactoryBean.setPersistenceUnitName(this.persistenceUnit);
      }
      entityManagerFactoryBean.setJpaVendorAdapter(jpaVendorAdapter);

      if (this.managedTypes != null) {
        entityManagerFactoryBean.setManagedTypes(this.managedTypes);
      } else {
        entityManagerFactoryBean.setPackagesToScan(this.packagesToScan);
      }
      entityManagerFactoryBean.getJpaPropertyMap().putAll(jpaProperties);
      entityManagerFactoryBean.getJpaPropertyMap().putAll(this.properties);
      if (!ObjectUtils.isEmpty(this.mappingResources)) {
        entityManagerFactoryBean.setMappingResources(this.mappingResources);
      }
      URL rootLocation = persistenceUnitRootLocation;
      if (rootLocation != null) {
        entityManagerFactoryBean.setPersistenceUnitRootLocation(rootLocation.toString());
      }
      if (bootstrapExecutor != null) {
        entityManagerFactoryBean.setBootstrapExecutor(bootstrapExecutor);
      }
      if (persistenceUnitPostProcessors != null) {
        entityManagerFactoryBean.setPersistenceUnitPostProcessors(persistenceUnitPostProcessors);
      }
      return entityManagerFactoryBean;
    }
  }
}
