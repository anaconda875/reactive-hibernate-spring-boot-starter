package com.htech.data.jpa.reactive.repository.query;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.ClassUtils;

public class QueryEnhancerFactory {

  private static final Log LOG = LogFactory.getLog(QueryEnhancerFactory.class);

  private static final boolean jSqlParserPresent =
      ClassUtils.isPresent(
          "net.sf.jsqlparser.parser.JSqlParser",
          org.springframework.data.jpa.repository.query.QueryEnhancerFactory.class
              .getClassLoader());

  static {
    if (jSqlParserPresent) {
      LOG.info("JSqlParser is in classpath; If applicable, JSqlParser will be used");
    }

    /* if (PersistenceProvider.ECLIPSELINK.isPresent()) {
      LOG.info("EclipseLink is in classpath; If applicable, EQL parser will be used.");
    }

    if (PersistenceProvider.HIBERNATE.isPresent()) {
      LOG.info("Hibernate is in classpath; If applicable, HQL parser will be used.");
    }*/

  }

  private QueryEnhancerFactory() {}

  /**
   * Creates a new {@link QueryEnhancer} for the given {@link
   * org.springframework.data.jpa.repository.query.DeclaredQuery}.
   *
   * @param query must not be {@literal null}.
   * @return an implementation of {@link QueryEnhancer} that suits the query the most
   */
  public static QueryEnhancer forQuery(DeclaredQuery query) {
    // TODO
    if (query.isNativeQuery()) {

      //      if (jSqlParserPresent) {
      //        /*
      //         * If JSqlParser fails, throw some alert signaling that people should write a custom
      // Impl.
      //         */
      //        return new JSqlParserQueryEnhancer(query);
      //      }

      return new DefaultQueryEnhancer(query);
    }

    //    if (PersistenceProvider.HIBERNATE.isPresent()) {
    //      return JpaQueryEnhancer.forHql(query);
    //    } else if (PersistenceProvider.ECLIPSELINK.isPresent()) {
    //      return JpaQueryEnhancer.forEql(query);
    //    } else {
    //      return JpaQueryEnhancer.forJpql(query);
    //    }

    try {
      return ReactiveJpaQueryEnhancer.forHql(query);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }
}
