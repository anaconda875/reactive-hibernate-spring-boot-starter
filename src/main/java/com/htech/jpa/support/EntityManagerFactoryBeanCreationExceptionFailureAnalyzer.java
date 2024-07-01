package com.htech.jpa.support;

import com.htech.jpa.reactive.ReactiveHibernateJpaConfiguration;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * @author Bao.Ngo
 */
public class EntityManagerFactoryBeanCreationExceptionFailureAnalyzer
    extends AbstractFailureAnalyzer<
        ReactiveHibernateJpaConfiguration.EntityManagerFactoryBeanCreationException>
    implements EnvironmentAware {

  private Environment environment;

  @Override
  protected FailureAnalysis analyze(
      Throwable rootFailure,
      ReactiveHibernateJpaConfiguration.EntityManagerFactoryBeanCreationException cause) {
    String description = getDescription(cause);
    String action = getAction(cause);
    return new FailureAnalysis(description, action, cause);
  }

  private String getDescription(
      ReactiveHibernateJpaConfiguration.EntityManagerFactoryBeanCreationException cause) {
    StringBuilder description = new StringBuilder();
    description.append("Failed to configure a EntityManagerFactoryBean: ");
    if (!StringUtils.hasText(cause.getJpaProps().get("jakarta.persistence.jdbc.url"))) {
      description.append("'url' attribute is not specified.");
    }
    //    description.append(String.format("no embedded database could be configured.%n"));
    description.append(String.format("%nReason: %s%n", cause.getMessage()));
    return description.toString();
  }

  private String getAction(
      ReactiveHibernateJpaConfiguration.EntityManagerFactoryBeanCreationException cause) {
    StringBuilder action = new StringBuilder();
    action.append(String.format("Consider the following:%n"));

    action.append(
        String.format(
            "\tReview the configuration of %s.\n",
            "spring.jpa.properties.jakarta.persistence.jdbc.url"));
    action
        .append(
            "\tIf you have database settings to be loaded from a particular "
                + "profile you may need to activate it")
        .append(getActiveProfiles());
    return action.toString();
  }

  private String getActiveProfiles() {
    StringBuilder message = new StringBuilder();
    String[] profiles = this.environment.getActiveProfiles();
    if (ObjectUtils.isEmpty(profiles)) {
      message.append(" (no profiles are currently active).");
    } else {
      message.append(" (the profiles ");
      message.append(StringUtils.arrayToCommaDelimitedString(profiles));
      message.append(" are currently active).");
    }
    return message.toString();
  }

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }
}
