package com.htech.jpa.pu;

import jakarta.persistence.spi.PersistenceUnitInfo;
import java.util.Map;
import java.util.Properties;
import org.springframework.orm.jpa.persistenceunit.DefaultPersistenceUnitManager;
import org.springframework.orm.jpa.persistenceunit.MutablePersistenceUnitInfo;

/**
 * @author Bao.Ngo
 */
public class CustomPersistenceUnitManager extends DefaultPersistenceUnitManager {

  private final Map<String, Object> vendorProperties;

  public CustomPersistenceUnitManager(Map<String, Object> vendorProperties) {
    this.vendorProperties = vendorProperties;
  }

  @Override
  public PersistenceUnitInfo obtainDefaultPersistenceUnitInfo() {
    PersistenceUnitInfo persistenceUnitInfo = super.obtainDefaultPersistenceUnitInfo();
    if (persistenceUnitInfo instanceof MutablePersistenceUnitInfo m) {
      Properties properties = new Properties();
      properties.putAll(vendorProperties);
      m.setProperties(properties);
    }

    return persistenceUnitInfo;
  }
}
