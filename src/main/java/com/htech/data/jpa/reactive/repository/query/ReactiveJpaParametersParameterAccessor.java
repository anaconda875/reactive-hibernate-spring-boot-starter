package com.htech.data.jpa.reactive.repository.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.hibernate.reactive.stage.Stage;
import org.reactivestreams.Publisher;
import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.util.ReactiveWrapperConverters;
import org.springframework.data.util.ReactiveWrappers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Bao.Ngo
 */
public class ReactiveJpaParametersParameterAccessor extends JpaParametersParameterAccessor {

  protected final ReactiveJpaQueryMethod method;
  protected final Object[] values;
  protected final Stage.SessionFactory sessionFactory;

  public ReactiveJpaParametersParameterAccessor(
      ReactiveJpaQueryMethod method,
      Parameters<?, ?> parameters,
      Object[] values,
      Stage.SessionFactory sessionFactory /*,
      Mono<Stage.Session> session,
      Stage.Session session,
      Stage.Transaction transaction*/) {
    super(parameters, values);
    this.method = method;
    this.values = values;
    this.sessionFactory = sessionFactory;
  }

  public Stage.SessionFactory getSessionFactory() {
    return sessionFactory;
  }

  @SuppressWarnings("unchecked")
  public Mono<ReactiveJpaParametersParameterAccessor> resolveParameters() {

    boolean hasReactiveWrapper = false;

    for (Object value : values) {
      if (value == null || !ReactiveWrappers.supports(value.getClass())) {
        continue;
      }

      hasReactiveWrapper = true;
      break;
    }

    if (!hasReactiveWrapper) {
      return Mono.just(this);
    }

    Object[] resolved = new Object[values.length];
    Map<Integer, Optional<?>> holder = new ConcurrentHashMap<>();
    List<Publisher<?>> publishers = new ArrayList<>();

    for (int i = 0; i < values.length; i++) {

      Object value = resolved[i] = values[i];
      if (value == null || !ReactiveWrappers.supports(value.getClass())) {
        continue;
      }

      if (ReactiveWrappers.isSingleValueType(value.getClass())) {

        int index = i;
        publishers.add(
            ReactiveWrapperConverters.toWrapper(value, Mono.class) //
                .map(Optional::of) //
                .defaultIfEmpty(Optional.empty()) //
                .doOnNext(it -> holder.put(index, (Optional<?>) it)));
      } else {

        int index = i;
        publishers.add(
            ReactiveWrapperConverters.toWrapper(value, Flux.class) //
                .collectList() //
                .doOnNext(it -> holder.put(index, Optional.of(it))));
      }
    }

    return Flux.merge(publishers)
        .then()
        .thenReturn(resolved)
        .map(
            values -> {
              holder.forEach((index, v) -> values[index] = v.orElse(null));
              return new ReactiveJpaParametersParameterAccessor(
                  method, getParameters(), values, sessionFactory);
            });
  }

  public Parameters<?, ?> getBindableParameters() {
    return getParameters().getBindableParameters();
  }
}
