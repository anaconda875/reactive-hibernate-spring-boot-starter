package com.htech.data.jpa.reactive.repository.query;

import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.data.expression.ValueEvaluationContext;
import org.springframework.data.expression.ValueExpression;
import org.springframework.data.expression.ValueExpressionParser;
import org.springframework.data.jpa.repository.query.JpaEntityMetadata;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;

/**
 * @author Bao.Ngo
 */
public class ExpressionBasedStringQuery extends StringQuery {

  private static final String EXPRESSION_PARAMETER = "$1#{";
  private static final String QUOTED_EXPRESSION_PARAMETER = "$1__HASH__{";

  private static final Pattern EXPRESSION_PARAMETER_QUOTING = Pattern.compile("([:?])#\\{");
  private static final Pattern EXPRESSION_PARAMETER_UNQUOTING =
      Pattern.compile("([:?])__HASH__\\{");

  private static final String ENTITY_NAME = "entityName";
  private static final String ENTITY_NAME_VARIABLE = "#" + ENTITY_NAME;
  private static final String ENTITY_NAME_VARIABLE_EXPRESSION = "#{" + ENTITY_NAME_VARIABLE;

  public ExpressionBasedStringQuery(
      String query,
      JpaEntityMetadata<?> metadata,
      ValueExpressionParser parser,
      boolean nativeQuery) {
    super(
        renderQueryIfExpressionOrReturnQuery(query, metadata, parser),
        nativeQuery && !containsExpression(query));
  }

  static ExpressionBasedStringQuery from(
      DeclaredQuery query,
      JpaEntityMetadata<?> metadata,
      ValueExpressionParser parser,
      boolean nativeQuery) {
    return new ExpressionBasedStringQuery(query.getQueryString(), metadata, parser, nativeQuery);
  }

  private static String renderQueryIfExpressionOrReturnQuery(
      String query, JpaEntityMetadata<?> metadata, ValueExpressionParser parser) {
    Assert.notNull(query, "query must not be null");
    Assert.notNull(metadata, "metadata must not be null");
    Assert.notNull(parser, "parser must not be null");

    if (!containsExpression(query)) {
      return query;
    }

    StandardEvaluationContext evalContext = new StandardEvaluationContext();
    evalContext.setVariable(ENTITY_NAME, metadata.getEntityName());

    query = potentiallyQuoteExpressionsParameter(query);

    ValueExpression expr = parser.parse(query);

    String result = Objects.toString(expr.evaluate(ValueEvaluationContext.of(null, evalContext)));

    if (result == null) {
      return query;
    }

    return potentiallyUnquoteParameterExpressions(result);
  }

  private static String potentiallyUnquoteParameterExpressions(String result) {
    return EXPRESSION_PARAMETER_UNQUOTING.matcher(result).replaceAll(EXPRESSION_PARAMETER);
  }

  private static String potentiallyQuoteExpressionsParameter(String query) {
    return EXPRESSION_PARAMETER_QUOTING.matcher(query).replaceAll(QUOTED_EXPRESSION_PARAMETER);
  }

  private static boolean containsExpression(String query) {
    return query.contains(ENTITY_NAME_VARIABLE_EXPRESSION);
  }
}
