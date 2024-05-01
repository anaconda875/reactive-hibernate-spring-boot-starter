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
  }

  private QueryEnhancerFactory() {}

  public static QueryEnhancer forQuery(DeclaredQuery query) {
    // TODO
    if (query.isNativeQuery()) {
      return new DefaultQueryEnhancer(query);
    }

    try {
      return ReactiveJpaQueryEnhancer.forHql(query);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }
}
