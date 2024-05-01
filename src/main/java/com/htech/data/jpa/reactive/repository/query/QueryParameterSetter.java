package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.Parameter;
import jakarta.persistence.Query;
import jakarta.persistence.TemporalType;
import jakarta.persistence.criteria.ParameterExpression;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Function;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.data.jpa.repository.query.JpaParametersParameterAccessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public interface QueryParameterSetter {

  void setParameter(
      QueryParameterSetter.BindableQuery query,
      JpaParametersParameterAccessor accessor,
      QueryParameterSetter.ErrorHandling errorHandling);

  QueryParameterSetter NOOP = (query, values, errorHandling) -> {};

  class NamedOrIndexedQueryParameterSetter implements QueryParameterSetter {

    //    protected final boolean useIndexBasedParams;
    protected final Function<JpaParametersParameterAccessor, Object> valueExtractor;
    protected final Parameter<?> parameter;
    protected final @Nullable TemporalType temporalType;

    NamedOrIndexedQueryParameterSetter(
        Function<JpaParametersParameterAccessor, Object> valueExtractor,
        Parameter<?> parameter,
        @Nullable TemporalType temporalType) {

      Assert.notNull(valueExtractor, "ValueExtractor must not be null");

      this.valueExtractor = valueExtractor;
      this.parameter = parameter;
      this.temporalType = temporalType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setParameter(
        BindableQuery query, JpaParametersParameterAccessor accessor, ErrorHandling errorHandling) {

      if (temporalType != null) {
        // TODO
      } else {

        Object value = valueExtractor.apply(accessor);

        if (parameter instanceof ParameterExpression) {
          errorHandling.execute(() -> query.setParameter((Parameter<Object>) parameter, value));
        } else if (parameter.getName() != null) {
          errorHandling.execute(() -> query.setParameter(parameter.getName(), value));

        } else {

          Integer position = parameter.getPosition();

          if (position != null) {
            errorHandling.execute(() -> query.setParameter(position, value));
          }
        }
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
        String cacheKey, Mutiny.AbstractQuery query) {

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

    QueryMetadata(Mutiny.AbstractQuery query) {

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

    public QueryParameterSetter.BindableQuery withQuery(Mutiny.AbstractQuery query) {
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

    protected final Mutiny.AbstractQuery query;

    //    protected final Mutiny.AbstractQuery unwrapped;

    BindableQuery(QueryParameterSetter.QueryMetadata metadata, Mutiny.AbstractQuery query) {
      super(metadata);
      this.query = query;
      //      this.unwrapped = Proxy.isProxyClass(query.getClass()) ? query.unwrap(null) : query;
    }

    private BindableQuery(Mutiny.AbstractQuery query) {
      super(query);
      this.query = query;
      //      this.unwrapped = Proxy.isProxyClass(query.getClass()) ? query.unwrap(null) : query;
    }

    public static QueryParameterSetter.BindableQuery from(Mutiny.AbstractQuery query) {
      return new QueryParameterSetter.BindableQuery(query);
    }

    public Mutiny.AbstractQuery getQuery() {
      return query;
    }

    public <T> Mutiny.AbstractQuery setParameter(Parameter<T> param, T value) {
      return query.setParameter(param, value);
    }

    public Mutiny.AbstractQuery setParameter(
        Parameter<Date> param, Date value, TemporalType temporalType) {
      return query.setParameter(param, value /*, temporalType*/);
    }

    public Mutiny.AbstractQuery setParameter(String name, Object value) {
      return query.setParameter(name, value);
    }

    public Mutiny.AbstractQuery setParameter(String name, Date value, TemporalType temporalType) {
      return query.setParameter(name, value /*, temporalType*/);
    }

    public Mutiny.AbstractQuery setParameter(int position, Object value) {
      return query.setParameter(position, value);
    }

    public Mutiny.AbstractQuery setParameter(int position, Date value, TemporalType temporalType) {
      return query.setParameter(position, value /*, temporalType*/);
    }
  }
}