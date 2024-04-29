// package com.example.demo.jpa.reactive.repository.query;
//
// import org.springframework.lang.Nullable;
// import org.springframework.util.StringUtils;
//
// import java.lang.reflect.Method;
// import java.util.Locale;
//
// public interface QueryLookupStrategy {
//
//  enum Key {
//
//    CREATE, USE_DECLARED_QUERY, CREATE_IF_NOT_FOUND;
//
//    /**
//     * Returns a strategy key from the given XML value.
//     *
//     * @param xml
//     * @return a strategy key from the given XML value
//     */
//    @Nullable
//    public static Key create(String xml) {
//
//      if (!StringUtils.hasText(xml)) {
//        return null;
//      }
//
//      return valueOf(xml.toUpperCase(Locale.US).replace("-", "_"));
//    }
//  }
//
//  /**
//   * Resolves a {@link RepositoryQuery} from the given {@link QueryMethod} that can be executed
// afterwards.
//   *
//   * @param method will never be {@literal null}.
//   * @param metadata will never be {@literal null}.
//   * @param factory will never be {@literal null}.
//   * @param namedQueries will never be {@literal null}.
//   * @return
//   */
//  RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata, ProjectionFactory
// factory,
//                               NamedQueries namedQueries);
// }
//
