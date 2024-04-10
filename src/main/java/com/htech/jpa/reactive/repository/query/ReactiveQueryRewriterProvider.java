package com.htech.jpa.reactive.repository.query;

import org.springframework.beans.BeanUtils;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.data.jpa.repository.query.QueryRewriterProvider;

public interface ReactiveQueryRewriterProvider {

  static ReactiveQueryRewriterProvider simple() {

    return method -> {

      Class<? extends QueryRewriter> queryRewriter = method.getQueryRewriter();

      if (queryRewriter == QueryRewriter.IdentityQueryRewriter.class) {
        return QueryRewriter.IdentityQueryRewriter.INSTANCE;
      }

      return BeanUtils.instantiateClass(queryRewriter);
    };
  }

  QueryRewriter getQueryRewriter(ReactiveJpaQueryMethod method);
}
