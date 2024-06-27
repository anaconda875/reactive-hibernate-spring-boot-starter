package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.Parameter;
import jakarta.persistence.Query;
import jakarta.persistence.TemporalType;
import jakarta.persistence.criteria.ParameterExpression;
import java.lang.reflect.Proxy;
import java.util.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.reactive.stage.Stage;
import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import org.springframework.data.util.NullableWrapperConverters;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

public interface QueryParameterSetter {

  Mono<Void> setParameter(
      QueryParameterSetter.BindableQuery query,
      JpaParametersParameterAccessor accessor,
      QueryParameterSetter.ErrorHandling errorHandling);

  QueryParameterSetter NOOP = (query, values, errorHandling) -> Mono.empty();

  class NamedOrIndexedQueryParameterSetter implements QueryParameterSetter {

    //    protected final boolean useIndexBasedParams;
    //    protected final Function<JpaParametersParameterAccessor, Object> valueExtractor;
    protected final ParameterValueEvaluator valueEvaluator;
    protected final ParameterBinding binding;
    protected final Parameter<?> parameter;
    protected final @Nullable TemporalType temporalType;

    NamedOrIndexedQueryParameterSetter(
        //        Function<JpaParametersParameterAccessor, Object> valueExtractor,
        ParameterValueEvaluator valueEvaluator,
        ParameterBinding binding,
        Parameter<?> parameter,
        @Nullable TemporalType temporalType) {
      Assert.notNull(valueEvaluator, "ValueEvaluator must not be null");

      this.valueEvaluator = valueEvaluator;
      this.binding = binding;
      this.parameter = parameter;
      this.temporalType = temporalType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Void> setParameter(
        BindableQuery query, JpaParametersParameterAccessor accessor, ErrorHandling errorHandling) {

      if (temporalType != null) {
        return Mono.empty();
        // TODO
      } else {

        return valueEvaluator
            .evaluate(accessor)
            .map(binding::prepare)
            .doOnNext(
                value -> {
                  Object unwrapped;
                  if (NullableWrapperConverters.supports(value.getClass())) {
                    unwrapped = NullableWrapperConverters.unwrap(value);
                  } else {
                    unwrapped = value;
                  }

                  if (parameter instanceof ParameterExpression) {
                    errorHandling.execute(
                        () -> query.setParameter((Parameter<Object>) parameter, unwrapped));
                  } else if (parameter.getName() != null) {
                    errorHandling.execute(() -> query.setParameter(parameter.getName(), unwrapped));

                  } else {
                    Integer position = parameter.getPosition();

                    if (position != null) {
                      errorHandling.execute(() -> query.setParameter(position, unwrapped));
                    }
                  }
                })
            .then();

        //        if (parameter instanceof ParameterExpression) {
        //          return errorHandling.execute(
        //              () -> query.setParameter((Parameter<Object>) parameter, value));
        //        } else if (parameter.getName() != null) {
        //          return errorHandling.execute(() -> query.setParameter(parameter.getName(),
        // value));
        //
        //        } else {
        //          Integer position = parameter.getPosition();
        //
        //          if (position != null) {
        //            return errorHandling.execute(() -> query.setParameter(position, value));
        //          }
        //        }

        //        return Mono.error(
        //            () -> new IllegalStateException("Illegal parameter " + parameter.getClass()));
      }
    }
  }

  enum ErrorHandling {
    STRICT {

      @Override
      public void execute(Runnable block) {
        block.run();
      }
    },

    LENIENT {

      @Override
      public void execute(Runnable block) {
        //        return Mono.fromRunnable(block)
        //            .onErrorResume(
        //                RuntimeException.class,
        //                rex -> {
        //                  LOG.info("Silently ignoring", rex);
        //                  return Mono.empty();
        //                })
        //            .then();
        try {
          block.run();
        } catch (RuntimeException rex) {
          LOG.info("Silently ignoring", rex);
        }
      }
    };

    private static final Log LOG = LogFactory.getLog(QueryParameterSetter.ErrorHandling.class);

    abstract void execute(Runnable block);
  }

  class QueryMetadataCache {

    private Map<String, QueryParameterSetter.QueryMetadata> cache = Collections.emptyMap();

    public QueryParameterSetter.QueryMetadata getMetadata(
        String cacheKey, Stage.AbstractQuery query) {
      QueryParameterSetter.QueryMetadata queryMetadata = cache.get(cacheKey);

      if (queryMetadata == null) {
        queryMetadata = new QueryParameterSetter.QueryMetadata(query);

        Map<String, QueryParameterSetter.QueryMetadata> cache;

        if (this.cache.isEmpty()) {
          cache = Collections.singletonMap(cacheKey, queryMetadata);
        } else {
          cache = new HashMap<>(this.cache);
          cache.put(cacheKey, queryMetadata);
        }

        synchronized (this) {
          this.cache = cache;
        }
      }

      return queryMetadata;
    }
  }

  class QueryMetadata {

    private final boolean namedParameters = false;
    private final Set<Parameter<?>> parameters = new HashSet<>();
    private final boolean registerExcessParameters = false;

    QueryMetadata(Stage.AbstractQuery query) {

      /* this.namedParameters = QueryUtils.hasNamedParameter(query);
      this.parameters = query.getParameters();

      // DATAJPA-1172
      // Since EclipseLink doesn't reliably report whether a query has parameters
      // we simply try to set the parameters and ignore possible failures.
      // this is relevant for native queries with SpEL expressions, where the method parameters don't have to match the
      // parameters in the query.
      // https://bugs.eclipse.org/bugs/show_bug.cgi?id=521915

      this.registerExcessParameters = query.getParameters().size() == 0
          && unwrapClass(query).getName().startsWith("org.eclipse");*/
    }

    QueryMetadata(QueryParameterSetter.QueryMetadata metadata) {

      //      this.namedParameters = metadata.namedParameters;
      //      this.parameters = metadata.parameters;
      //      this.registerExcessParameters = metadata.registerExcessParameters;
    }

    public QueryParameterSetter.BindableQuery withQuery(Stage.AbstractQuery query) {
      return new QueryParameterSetter.BindableQuery(this, query);
    }

    public Set<Parameter<?>> getParameters() {
      return parameters;
    }

    public boolean hasNamedParameters() {
      return this.namedParameters;
    }

    public boolean registerExcessParameters() {
      return this.registerExcessParameters;
    }

    private static Class<?> unwrapClass(Query query) {

      Class<? extends Query> queryType = query.getClass();

      try {

        return Proxy.isProxyClass(queryType) //
            ? query.unwrap(null).getClass() //
            : queryType;

      } catch (RuntimeException e) {

        LogFactory.getLog(QueryParameterSetter.QueryMetadata.class)
            .warn("Failed to unwrap actual class for Query proxy", e);

        return queryType;
      }
    }
  }

  class BindableQuery extends QueryMetadata {

    protected final Stage.AbstractQuery query;

    //    protected final Stage.AbstractQuery unwrapped;

    BindableQuery(QueryParameterSetter.QueryMetadata metadata, Stage.AbstractQuery query) {
      super(metadata);
      this.query = query;
      //      this.unwrapped = Proxy.isProxyClass(query.getClass()) ? query.unwrap(null) : query;
    }

    private BindableQuery(Stage.AbstractQuery query) {
      super(query);
      this.query = query;
      //      this.unwrapped = Proxy.isProxyClass(query.getClass()) ? query.unwrap(null) : query;
    }

    public static QueryParameterSetter.BindableQuery from(Stage.AbstractQuery query) {
      return new QueryParameterSetter.BindableQuery(query);
    }

    public Stage.AbstractQuery getQuery() {
      return query;
    }

    public <T> Stage.AbstractQuery setParameter(Parameter<T> param, T value) {
      return query.setParameter(param, value);
    }

    public Stage.AbstractQuery setParameter(
        Parameter<Date> param, Date value, TemporalType temporalType) {
      return query.setParameter(param, value /*, temporalType*/);
    }

    public Stage.AbstractQuery setParameter(String name, Object value) {
      return query.setParameter(name, value);
    }

    public Stage.AbstractQuery setParameter(String name, Date value, TemporalType temporalType) {
      return query.setParameter(name, value /*, temporalType*/);
    }

    public Stage.AbstractQuery setParameter(int position, Object value) {
      return query.setParameter(position, value);
    }

    public Stage.AbstractQuery setParameter(int position, Date value, TemporalType temporalType) {
      return query.setParameter(position, value /*, temporalType*/);
    }
  }
}
