package com.htech.jpa.reactive.repository.auto;

import com.htech.jpa.reactive.ReactiveHibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({ ReactiveHibernateJpaAutoConfiguration.class, TaskExecutionAutoConfiguration.class })
@Import(ReactiveJpaRepositoriesAutoConfiguration.ReactiveJpaRepositoriesImportSelector.class)
public class ReactiveJpaRepositoriesAutoConfiguration {

  static class ReactiveJpaRepositoriesImportSelector implements ImportSelector {


    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
      return new String[] { determineImport() };
    }

    private String determineImport() {
      return ReactiveJpaRepositoriesRegistrar1.class.getName();
    }

  }
}
