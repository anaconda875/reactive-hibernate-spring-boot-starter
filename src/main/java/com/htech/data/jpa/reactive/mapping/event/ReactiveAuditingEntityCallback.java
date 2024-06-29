package com.htech.data.jpa.reactive.mapping.event;

import org.reactivestreams.Publisher;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.core.Ordered;
import org.springframework.data.auditing.ReactiveIsNewAwareAuditingHandler;
import org.springframework.util.Assert;

/**
 * @author Bao.Ngo
 */
public class ReactiveAuditingEntityCallback implements BeforeSaveCallback<Object>, Ordered {

  private final ObjectFactory<ReactiveIsNewAwareAuditingHandler> auditingHandlerFactory;

  public ReactiveAuditingEntityCallback(
      ObjectFactory<ReactiveIsNewAwareAuditingHandler> auditingHandlerFactory) {
    Assert.notNull(auditingHandlerFactory, "IsNewAwareAuditingHandler must not be null");
    this.auditingHandlerFactory = auditingHandlerFactory;
  }

  @Override
  public Publisher<Object> onBeforeConvert(Object entity) {
    return auditingHandlerFactory.getObject().markAudited(entity);
  }

  @Override
  public int getOrder() {
    return 100;
  }
}
