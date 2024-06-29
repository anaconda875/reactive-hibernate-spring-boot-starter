package com.htech.data.jpa.reactive.repository.query;

import org.springframework.beans.BeanUtils;
import org.springframework.data.jpa.repository.QueryRewriter;

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
