package com.htech.data.jpa.reactive.mapping.event;

import org.reactivestreams.Publisher;
import org.springframework.data.mapping.callback.EntityCallback;

@FunctionalInterface
public interface BeforeSaveCallback<T> extends EntityCallback<T> {

  Publisher<T> onBeforeConvert(T entity);
}
