package com.htech.data.jpa.reactive.mapping.event;

import org.reactivestreams.Publisher;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.core.Ordered;
import org.springframework.data.auditing.ReactiveIsNewAwareAuditingHandler;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

public class ReactiveAuditingEntityCallback implements BeforeSaveCallback<Object>, Ordered {

  private final ObjectFactory<ReactiveIsNewAwareAuditingHandler> auditingHandlerFactory;

  public ReactiveAuditingEntityCallback(
      ObjectFactory<ReactiveIsNewAwareAuditingHandler> auditingHandlerFactory) {
    Assert.notNull(auditingHandlerFactory, "IsNewAwareAuditingHandler must not be null");
    this.auditingHandlerFactory = auditingHandlerFactory;
  }

  @Override
  public Publisher<Object> onBeforeConvert(Object entity) {
    return Mono.deferContextual(
            c -> {
              Mono<?> authority = c.<Mono>getOrEmpty("AUTHORITY").orElse(Mono.empty());
              return authority;
            })
        .then(auditingHandlerFactory.getObject().markAudited(entity));
  }

  @Override
  public int getOrder() {
    return 100;
  }
}
