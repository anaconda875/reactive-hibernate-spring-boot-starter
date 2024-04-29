package com.htech.data.jpa.reactive.repository.query;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

public class ReactiveJpaQueryEnhancer implements QueryEnhancer {

  protected final DeclaredQuery query;
  protected final Object queryParser;

  private ReactiveJpaQueryEnhancer(DeclaredQuery query, Object queryParser) {
    this.query = query;
    this.queryParser = queryParser;
  }

  public static ReactiveJpaQueryEnhancer forJpql(DeclaredQuery query) throws Exception {
    Assert.notNull(query, "DeclaredQuery must not be null!");

    Class<?> clazz = Class.forName("org.springframework.data.jpa.repository.query.JpqlQueryParser");
    Constructor<?> constructor = ReflectionUtils.accessibleConstructor(clazz, String.class);
    return new ReactiveJpaQueryEnhancer(query, constructor.newInstance(query.getQueryString()));
  }

  public static ReactiveJpaQueryEnhancer forHql(DeclaredQuery query) throws Exception {
    Assert.notNull(query, "DeclaredQuery must not be null!");

    Class<?> clazz = Class.forName("org.springframework.data.jpa.repository.query.HqlQueryParser");
    Constructor<?> constructor = ReflectionUtils.accessibleConstructor(clazz, String.class);
    return new ReactiveJpaQueryEnhancer(query, constructor.newInstance(query.getQueryString()));
  }

  /*public static ReactiveJpaQueryEnhancer forEql(DeclaredQuery query) {

    Assert.notNull(query, "DeclaredQuery must not be null!");

    return new ReactiveJpaQueryEnhancer(query, new EqlQueryParser(query.getQueryString()));
  }*/

  protected Object getQueryParsingStrategy() {
    return queryParser;
  }

  @Override
  public String applySorting(Sort sort) {
    return invokeQueryParser("renderSortedQuery", new Class[] {Sort.class}, sort);
  }

  private <T> T invokeQueryParser(
      String methodName, Class<?>[] methodParamTypes, Object... params) {
    try {
      Method method;
      if (methodParamTypes == null || methodParamTypes.length == 0) {
        method = ReflectionUtils.findMethod(queryParser.getClass(), methodName);
      } else {
        method = ReflectionUtils.findMethod(queryParser.getClass(), methodName, methodParamTypes);
      }
      ReflectionUtils.makeAccessible(method);

      if (params == null || params.length == 0) {
        return (T) method.invoke(queryParser);
      }

      return (T) method.invoke(queryParser, params);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public String applySorting(Sort sort, String alias) {
    return applySorting(sort);
  }

  @Override
  public String detectAlias() {
    return invokeQueryParser("findAlias", null);
    //    try {
    //      Method method = ReflectionUtils.findMethod(queryParser.getClass(), "findAlias");
    //      ReflectionUtils.makeAccessible(method);
    //      return (String) method.invoke(queryParser);
    //    } catch (Exception e) {
    //      throw new RuntimeException(e.getMessage(), e);
    //    }
  }

  /**
   * Creates a count query from the original query, with no count projection.
   *
   * @return Guaranteed to be not {@literal null};
   */
  @Override
  public String createCountQueryFor() {
    return createCountQueryFor(null);
  }

  /**
   * Create a count query from the original query, with potential custom projection.
   *
   * @param countProjection may be {@literal null}.
   */
  @Override
  public String createCountQueryFor(@Nullable String countProjection) {
    return invokeQueryParser("createCountQuery", new Class[] {String.class}, countProjection);
    /*try {
      Method method = ReflectionUtils.findMethod(queryParser.getClass(), "createCountQuery", String.class);
      ReflectionUtils.makeAccessible(method);
      return (String) method.invoke(queryParser, countProjection);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }*/
  }

  /**
   * Checks if the select clause has a new constructor instantiation in the JPA query.
   *
   * @return Guaranteed to return {@literal true} or {@literal false}.
   */
  @Override
  public boolean hasConstructorExpression() {
    return invokeQueryParser("hasConstructorExpression", null);
    /*try {
      Method method = ReflectionUtils.findMethod(queryParser.getClass(), "hasConstructorExpression");
      ReflectionUtils.makeAccessible(method);
      return (boolean) method.invoke(queryParser);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }*/
  }

  @Override
  public String getProjection() {
    return invokeQueryParser("projection", null);
    /*try {
      Method method = ReflectionUtils.findMethod(queryParser.getClass(), "projection");
      ReflectionUtils.makeAccessible(method);
      return (String) method.invoke(queryParser);
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }*/
  }

  @Override
  public Set<String> getJoinAliases() {
    return Set.of();
  }

  @Override
  public DeclaredQuery getQuery() {
    return query;
  }
}
