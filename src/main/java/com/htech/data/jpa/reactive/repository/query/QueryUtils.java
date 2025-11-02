package com.htech.data.jpa.reactive.repository.query;

import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.*;
import static java.util.regex.Pattern.*;
import static java.util.regex.Pattern.compile;

import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.util.Streamable;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class QueryUtils {

  public static final String COUNT_QUERY_STRING = "select count(%s) from %s x";
  public static final String DELETE_ALL_QUERY_STRING = "delete from %s x";
  public static final String DELETE_ALL_QUERY_BY_ID_STRING = "delete from %s x where %s in :ids";

  // Used Regex/Unicode categories (see
  // https://www.unicode.org/reports/tr18/#General_Category_Property):
  // Z Separator
  // Cc Control
  // Cf Format
  // Punct Punctuation
  private static final String IDENTIFIER = "[._$[\\P{Z}&&\\P{Cc}&&\\P{Cf}&&\\P{Punct}]]+";
  static final String COLON_NO_DOUBLE_COLON = "(?<![:\\\\]):";
  static final String IDENTIFIER_GROUP = String.format("(%s)", IDENTIFIER);

  private static final String COUNT_REPLACEMENT_TEMPLATE = "select count(%s) $5$6$7";
  private static final String SIMPLE_COUNT_VALUE = "$2";
  private static final String COMPLEX_COUNT_VALUE = "$3 $6";
  private static final String COMPLEX_COUNT_LAST_VALUE = "$6";
  private static final String ORDER_BY_PART = "(?iu)\\s+order\\s+by\\s+.*";

  private static final Pattern ALIAS_MATCH;
  private static final Pattern COUNT_MATCH;
  private static final Pattern STARTS_WITH_PAREN = Pattern.compile("^\\s*\\(");
  private static final Pattern PARENS_TO_REMOVE =
      Pattern.compile("(\\(.*\\bfrom\\b[^)]+\\))", CASE_INSENSITIVE | DOTALL | MULTILINE);
  private static final Pattern PROJECTION_CLAUSE =
      Pattern.compile("select\\s+(?:distinct\\s+)?(.+)\\s+from", Pattern.CASE_INSENSITIVE);

  private static final Pattern NO_DIGITS = Pattern.compile("\\D+");

  private static final String JOIN =
      "join\\s+(fetch\\s+)?" + IDENTIFIER + "\\s+(as\\s+)?" + IDENTIFIER_GROUP;
  private static final Pattern JOIN_PATTERN = Pattern.compile(JOIN, Pattern.CASE_INSENSITIVE);

  private static final String EQUALS_CONDITION_STRING = "%s.%s = :%s";
  private static final Pattern ORDER_BY = Pattern.compile("(order\\s+by\\s+)", CASE_INSENSITIVE);
  private static final Pattern ORDER_BY_IN_WINDOW_OR_SUBSELECT =
      Pattern.compile("\\([\\s\\S]*order\\s+by\\s[\\s\\S]*\\)", CASE_INSENSITIVE);

  private static final Pattern NAMED_PARAMETER =
      Pattern.compile(COLON_NO_DOUBLE_COLON + IDENTIFIER + "|#" + IDENTIFIER, CASE_INSENSITIVE);

  private static final Pattern CONSTRUCTOR_EXPRESSION;

  private static final Map<Attribute.PersistentAttributeType, Class<? extends Annotation>>
      ASSOCIATION_TYPES;

  private static final int QUERY_JOIN_ALIAS_GROUP_INDEX = 3;
  private static final int VARIABLE_NAME_GROUP_INDEX = 4;
  private static final int COMPLEX_COUNT_FIRST_INDEX = 3;

  private static final Pattern PUNCTATION_PATTERN = Pattern.compile(".*((?![._])[\\p{Punct}|\\s])");
  private static final Pattern FUNCTION_PATTERN;
  private static final Pattern FIELD_ALIAS_PATTERN;

  private static final String UNSAFE_PROPERTY_REFERENCE =
      "Sort expression '%s' must only contain property references or "
          + "aliases used in the select clause; If you really want to use something other than that for sorting, please use "
          + "JpaSort.unsafe(…)";

  static {
    StringBuilder builder = new StringBuilder();
    builder.append("(?<=\\bfrom)"); // from as starting delimiter
    builder.append("(?:\\s)+"); // at least one space separating
    builder.append(IDENTIFIER_GROUP); // Entity name, can be qualified (any
    builder.append("(?:\\sas)*"); // exclude possible "as" keyword
    builder.append("(?:\\s)+"); // at least one space separating
    builder.append("(?!(?:where|group\\s*by|order\\s*by))(\\w+)"); // the actual alias

    ALIAS_MATCH = compile(builder.toString(), CASE_INSENSITIVE);

    builder = new StringBuilder();
    builder.append("\\s*");
    builder.append("(select\\s+((distinct)?((?s).+?)?)\\s+)?(from\\s+");
    builder.append(IDENTIFIER);
    builder.append("(?:\\s+as)?\\s+)");
    builder.append(IDENTIFIER_GROUP);
    builder.append("(.*)");

    COUNT_MATCH = compile(builder.toString(), CASE_INSENSITIVE | DOTALL);

    Map<Attribute.PersistentAttributeType, Class<? extends Annotation>> persistentAttributeTypes =
        new HashMap<>();
    persistentAttributeTypes.put(ONE_TO_ONE, OneToOne.class);
    persistentAttributeTypes.put(ONE_TO_MANY, null);
    persistentAttributeTypes.put(MANY_TO_ONE, ManyToOne.class);
    persistentAttributeTypes.put(MANY_TO_MANY, null);
    persistentAttributeTypes.put(ELEMENT_COLLECTION, null);

    ASSOCIATION_TYPES = Collections.unmodifiableMap(persistentAttributeTypes);

    builder = new StringBuilder();
    builder.append("select");
    builder.append("\\s+"); // at least one space separating
    builder.append(
        "(.*\\s+)?"); // anything in between (e.g. distinct) at least one space separating
    builder.append("new");
    builder.append("\\s+"); // at least one space separating
    builder.append(IDENTIFIER);
    builder.append("\\s*"); // zero to unlimited space separating
    builder.append("\\(");
    builder.append(".*");
    builder.append("\\)");

    CONSTRUCTOR_EXPRESSION = compile(builder.toString(), CASE_INSENSITIVE + DOTALL);

    builder = new StringBuilder();
    // any function call including parameters within the brackets
    builder.append("\\w+\\s*\\([\\w\\.,\\s'=:;\\\\?]+\\)");
    // the potential alias
    builder.append("\\s+[as|AS]+\\s+(([\\w\\.]+))");

    FUNCTION_PATTERN = compile(builder.toString());

    builder = new StringBuilder();
    builder.append("\\s+"); // at least one space
    builder.append("[^\\s\\(\\)]+"); // No white char no bracket
    builder.append("\\s+[as|AS]+\\s+(([\\w\\.]+))"); // the potential alias

    FIELD_ALIAS_PATTERN = compile(builder.toString());
  }

  private QueryUtils() {}

  public static String getExistsQueryString(
      String entityName, String countQueryPlaceHolder, Iterable<String> idAttributes) {

    String whereClause =
        Streamable.of(idAttributes).stream()
            .map(
                idAttribute ->
                    String.format(EQUALS_CONDITION_STRING, "x", idAttribute, idAttribute))
            .collect(Collectors.joining(" AND ", " WHERE ", ""));

    return String.format(COUNT_QUERY_STRING, countQueryPlaceHolder, entityName) + whereClause;
  }

  public static String getQueryString(String template, String entityName) {

    Assert.hasText(entityName, "Entity name must not be null or empty");

    return String.format(template, entityName);
  }

  public static String applySorting(String query, Sort sort) {
    return applySorting(query, sort, detectAlias(query));
  }

  public static String applySorting(String query, Sort sort, @Nullable String alias) {
    Assert.notNull(query, "Query must not be null");

    if (sort.isUnsorted()) {
      return query;
    }

    StringBuilder builder = new StringBuilder(query);

    if (hasOrderByClause(query)) {
      builder.append(", ");
    } else {
      builder.append(" order by ");
    }

    Set<String> joinAliases = getOuterJoinAliases(query);
    Set<String> selectionAliases = getFunctionAliases(query);
    selectionAliases.addAll(getFieldAliases(query));

    for (Sort.Order order : sort) {
      builder.append(getOrderClause(joinAliases, selectionAliases, alias, order)).append(", ");
    }

    builder.delete(builder.length() - 2, builder.length());

    return builder.toString();
  }

  private static boolean hasOrderByClause(String query) {
    return countOccurrences(ORDER_BY, query)
        > countOccurrences(ORDER_BY_IN_WINDOW_OR_SUBSELECT, query);
  }

  private static int countOccurrences(Pattern pattern, String string) {
    Matcher matcher = pattern.matcher(string);

    int occurrences = 0;
    while (matcher.find()) {
      occurrences++;
    }
    return occurrences;
  }

  private static String getOrderClause(
      Set<String> joinAliases,
      Set<String> selectionAlias,
      @Nullable String alias,
      Sort.Order order) {
    String property = order.getProperty();
    checkSortExpression(order);

    if (selectionAlias.contains(property)) {
      return String.format(
          "%s %s",
          order.isIgnoreCase() ? String.format("lower(%s)", property) : property,
          toJpaDirection(order));
    }

    boolean qualifyReference = !property.contains("("); // ( indicates a function
    for (String joinAlias : joinAliases) {
      if (property.startsWith(joinAlias.concat("."))) {

        qualifyReference = false;
        break;
      }
    }

    String reference =
        qualifyReference && StringUtils.hasText(alias)
            ? String.format("%s.%s", alias, property)
            : property;
    String wrapped = order.isIgnoreCase() ? String.format("lower(%s)", reference) : reference;

    return String.format("%s %s", wrapped, toJpaDirection(order));
  }

  static Set<String> getOuterJoinAliases(String query) {
    Set<String> result = new HashSet<>();
    Matcher matcher = JOIN_PATTERN.matcher(query);

    while (matcher.find()) {
      String alias = matcher.group(QUERY_JOIN_ALIAS_GROUP_INDEX);
      if (StringUtils.hasText(alias)) {
        result.add(alias);
      }
    }

    return result;
  }

  private static Set<String> getFieldAliases(String query) {
    Set<String> result = new HashSet<>();
    Matcher matcher = FIELD_ALIAS_PATTERN.matcher(query);

    while (matcher.find()) {
      String alias = matcher.group(1);

      if (StringUtils.hasText(alias)) {
        result.add(alias);
      }
    }
    return result;
  }

  static Set<String> getFunctionAliases(String query) {
    Set<String> result = new HashSet<>();
    Matcher matcher = FUNCTION_PATTERN.matcher(query);

    while (matcher.find()) {
      String alias = matcher.group(1);

      if (StringUtils.hasText(alias)) {
        result.add(alias);
      }
    }

    return result;
  }

  private static String toJpaDirection(Sort.Order order) {
    return order.getDirection().name().toLowerCase(Locale.US);
  }

  @Nullable
  @Deprecated
  public static String detectAlias(String query) {
    String alias = null;
    Matcher matcher = ALIAS_MATCH.matcher(removeSubqueries(query));
    while (matcher.find()) {
      alias = matcher.group(2);
    }
    return alias;
  }

  static String removeSubqueries(String query) {
    if (!StringUtils.hasText(query)) {
      return query;
    }

    List<Integer> opens = new ArrayList<>();
    List<Integer> closes = new ArrayList<>();
    List<Boolean> closeMatches = new ArrayList<>();

    for (int i = 0; i < query.length(); i++) {
      char c = query.charAt(i);
      if (c == '(') {
        opens.add(i);
      } else if (c == ')') {
        closes.add(i);
        closeMatches.add(Boolean.FALSE);
      }
    }

    StringBuilder sb = new StringBuilder(query);
    boolean startsWithParen = STARTS_WITH_PAREN.matcher(query).find();
    for (int i = opens.size() - 1; i >= (startsWithParen ? 1 : 0); i--) {
      Integer open = opens.get(i);
      Integer close = findClose(open, closes, closeMatches) + 1;

      if (close > open) {
        String subquery = sb.substring(open, close);
        Matcher matcher = PARENS_TO_REMOVE.matcher(subquery);
        if (matcher.find()) {
          sb.replace(open, close, new String(new char[close - open]).replace('\0', ' '));
        }
      }
    }

    return sb.toString();
  }

  private static Integer findClose(
      final Integer open, final List<Integer> closes, final List<Boolean> closeMatches) {
    for (int i = 0; i < closes.size(); i++) {
      int close = closes.get(i);
      if (close > open && !closeMatches.get(i)) {
        closeMatches.set(i, Boolean.TRUE);
        return close;
      }
    }

    return -1;
  }

  public static <T> Query applyAndBind(
      String queryString, Iterable<T> entities, EntityManager entityManager) {
    Assert.notNull(queryString, "Querystring must not be null");
    Assert.notNull(entities, "Iterable of entities must not be null");
    Assert.notNull(entityManager, "EntityManager must not be null");

    Iterator<T> iterator = entities.iterator();

    if (!iterator.hasNext()) {
      return entityManager.createQuery(queryString);
    }

    String alias = detectAlias(queryString);
    StringBuilder builder = new StringBuilder(queryString);
    builder.append(" where");

    int i = 0;
    while (iterator.hasNext()) {
      iterator.next();

      builder.append(String.format(" %s = ?%d", alias, ++i));

      if (iterator.hasNext()) {
        builder.append(" or");
      }
    }

    Query query = entityManager.createQuery(builder.toString());
    iterator = entities.iterator();
    i = 0;

    while (iterator.hasNext()) {
      query.setParameter(++i, iterator.next());
    }

    return query;
  }

  @Deprecated
  public static String createCountQueryFor(String originalQuery) {
    return createCountQueryFor(originalQuery, null);
  }

  @Deprecated
  public static String createCountQueryFor(String originalQuery, @Nullable String countProjection) {
    return createCountQueryFor(originalQuery, countProjection, false);
  }

  static String createCountQueryFor(
      String originalQuery, @Nullable String countProjection, boolean nativeQuery) {
    Assert.hasText(originalQuery, "OriginalQuery must not be null or empty");

    Matcher matcher = COUNT_MATCH.matcher(originalQuery);
    String countQuery;

    if (countProjection == null) {
      String variable = matcher.matches() ? matcher.group(VARIABLE_NAME_GROUP_INDEX) : null;
      boolean useVariable =
          StringUtils.hasText(variable)
              && !variable.startsWith("new") // select [new com.example.User...
              && !variable.startsWith(" new") // select distinct[ new com.example.User...
              && !variable.startsWith("count(") // select [count(...
              && !variable.contains(",");

      String complexCountValue =
          matcher.matches() && StringUtils.hasText(matcher.group(COMPLEX_COUNT_FIRST_INDEX))
              ? COMPLEX_COUNT_VALUE
              : COMPLEX_COUNT_LAST_VALUE;

      String replacement = useVariable ? SIMPLE_COUNT_VALUE : complexCountValue;
      if (variable != null && (nativeQuery && (variable.contains(",") || "*".equals(variable)))) {
        replacement = "1";
      } else {
        String alias =
            org.springframework.data.jpa.repository.query.QueryUtils.detectAlias(originalQuery);
        if (("*".equals(variable) && alias != null)) {
          replacement = alias;
        }
      }

      countQuery = matcher.replaceFirst(String.format(COUNT_REPLACEMENT_TEMPLATE, replacement));
    } else {
      countQuery = matcher.replaceFirst(String.format(COUNT_REPLACEMENT_TEMPLATE, countProjection));
    }

    return countQuery.replaceFirst(ORDER_BY_PART, "");
  }

  public static boolean hasNamedParameter(Query query) {
    Assert.notNull(query, "Query must not be null");
    for (Parameter<?> parameter : query.getParameters()) {
      String name = parameter.getName();

      // Hibernate 3 specific hack as it returns the index as String for the name.
      if (name != null && NO_DIGITS.matcher(name).find()) {
        return true;
      }
    }

    return false;
  }

  @Deprecated
  static boolean hasNamedParameter(@Nullable String query) {
    return StringUtils.hasText(query) && NAMED_PARAMETER.matcher(query).find();
  }

  public static List<jakarta.persistence.criteria.Order> toOrders(
      Sort sort, From<?, ?> from, CriteriaBuilder cb) {
    if (sort.isUnsorted()) {
      return Collections.emptyList();
    }

    Assert.notNull(from, "From must not be null");
    Assert.notNull(cb, "CriteriaBuilder must not be null");

    List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
    for (org.springframework.data.domain.Sort.Order order : sort) {
      orders.add(toJpaOrder(order, from, cb));
    }

    return orders;
  }

  public static boolean hasConstructorExpression(String query) {
    Assert.hasText(query, "Query must not be null or empty");

    return CONSTRUCTOR_EXPRESSION.matcher(query).find();
  }

  public static String getProjection(String query) {
    Assert.hasText(query, "Query must not be null or empty");

    Matcher matcher = PROJECTION_CLAUSE.matcher(query);
    String projection = matcher.find() ? matcher.group(1) : "";
    return projection.trim();
  }

  @SuppressWarnings("unchecked")
  public static jakarta.persistence.criteria.Order toJpaOrder(
      Sort.Order order, From<?, ?> from, CriteriaBuilder cb) {
    PropertyPath property = PropertyPath.from(order.getProperty(), from.getJavaType());
    Expression<?> expression = toExpressionRecursively(from, property);

    if (order.isIgnoreCase() && String.class.equals(expression.getJavaType())) {
      Expression<String> upper = cb.lower((Expression<String>) expression);
      return order.isAscending() ? cb.asc(upper) : cb.desc(upper);
    } else {
      return order.isAscending() ? cb.asc(expression) : cb.desc(expression);
    }
  }

  public static <T> Expression<T> toExpressionRecursively(From<?, ?> from, PropertyPath property) {
    return toExpressionRecursively(from, property, false);
  }

  public static <T> Expression<T> toExpressionRecursively(
      From<?, ?> from, PropertyPath property, boolean isForSelection) {
    return toExpressionRecursively(from, property, isForSelection, false);
  }

  @SuppressWarnings("unchecked")
  public static <T> Expression<T> toExpressionRecursively(
      From<?, ?> from,
      PropertyPath property,
      boolean isForSelection,
      boolean hasRequiredOuterJoin) {
    String segment = property.getSegment();
    boolean isLeafProperty = !property.hasNext();
    boolean requiresOuterJoin =
        requiresOuterJoin(from, property, isForSelection, hasRequiredOuterJoin);

    // if it does not require an outer join and is a leaf, simply get the segment
    if (!requiresOuterJoin && isLeafProperty) {
      return from.get(segment);
    }

    // get or create the join
    JoinType joinType = requiresOuterJoin ? JoinType.LEFT : JoinType.INNER;
    Join<?, ?> join = getOrCreateJoin(from, segment, joinType);

    // if it's a leaf, return the join
    if (isLeafProperty) {
      return (Expression<T>) join;
    }

    PropertyPath nextProperty =
        Objects.requireNonNull(property.next(), "An element of the property path is null");

    // recurse with the next property
    return toExpressionRecursively(join, nextProperty, isForSelection, requiresOuterJoin);
  }

  private static boolean requiresOuterJoin(
      From<?, ?> from,
      PropertyPath property,
      boolean isForSelection,
      boolean hasRequiredOuterJoin) {
    String segment = property.getSegment();

    // already inner joined so outer join is useless
    if (isAlreadyInnerJoined(from, segment)) return false;

    Bindable<?> propertyPathModel;
    Bindable<?> model = from.getModel();

    // required for EclipseLink: we try to avoid using from.get as EclipseLink produces an inner
    // join
    // regardless of which join operation is specified next
    // see: https://bugs.eclipse.org/bugs/show_bug.cgi?id=413892
    // still occurs as of 2.7
    ManagedType<?> managedType = null;
    if (model instanceof ManagedType) {
      managedType = (ManagedType<?>) model;
    } else if (model instanceof SingularAttribute
        && ((SingularAttribute<?, ?>) model).getType() instanceof ManagedType) {
      managedType = (ManagedType<?>) ((SingularAttribute<?, ?>) model).getType();
    }
    if (managedType != null) {
      propertyPathModel = (Bindable<?>) managedType.getAttribute(segment);
    } else {
      propertyPathModel = from.get(segment).getModel();
    }

    // is the attribute of Collection type?
    boolean isPluralAttribute = model instanceof PluralAttribute;
    boolean isLeafProperty = !property.hasNext();
    if (propertyPathModel == null && isPluralAttribute) {
      return true;
    }

    if (!(propertyPathModel instanceof Attribute<?, ?> attribute)) {
      return false;
    }

    // not a persistent attribute type association (@OneToOne, @ManyToOne)
    if (!ASSOCIATION_TYPES.containsKey(attribute.getPersistentAttributeType())) {
      return false;
    }

    boolean isCollection = attribute.isCollection();
    // if this path is an optional one to one attribute navigated from the not owning side we also
    // need an
    // explicit outer join to avoid https://hibernate.atlassian.net/browse/HHH-12712
    // and https://github.com/eclipse-ee4j/jpa-api/issues/170
    boolean isInverseOptionalOneToOne =
        Attribute.PersistentAttributeType.ONE_TO_ONE == attribute.getPersistentAttributeType()
            && StringUtils.hasText(getAnnotationProperty(attribute, "mappedBy", ""));

    if (isLeafProperty
        && !isForSelection
        && !isCollection
        && !isInverseOptionalOneToOne
        && !hasRequiredOuterJoin) {
      return false;
    }

    return hasRequiredOuterJoin || getAnnotationProperty(attribute, "optional", true);
  }

  @Nullable
  private static <T> T getAnnotationProperty(
      Attribute<?, ?> attribute, String propertyName, T defaultValue) {
    Class<? extends Annotation> associationAnnotation =
        ASSOCIATION_TYPES.get(attribute.getPersistentAttributeType());

    if (associationAnnotation == null) {
      return defaultValue;
    }

    Member member = attribute.getJavaMember();

    if (!(member instanceof AnnotatedElement annotatedMember)) {
      return defaultValue;
    }

    Annotation annotation = AnnotationUtils.getAnnotation(annotatedMember, associationAnnotation);
    return annotation == null
        ? defaultValue
        : (T) AnnotationUtils.getValue(annotation, propertyName);
  }

  private static Join<?, ?> getOrCreateJoin(From<?, ?> from, String attribute, JoinType joinType) {
    for (Join<?, ?> join : from.getJoins()) {
      if (join.getAttribute().getName().equals(attribute)) {
        return join;
      }
    }
    return from.join(attribute, joinType);
  }

  private static boolean isAlreadyInnerJoined(From<?, ?> from, String attribute) {
    for (Fetch<?, ?> fetch : from.getFetches()) {
      if (fetch.getAttribute().getName().equals(attribute)
          && fetch.getJoinType().equals(JoinType.INNER)) {
        return true;
      }
    }

    for (Join<?, ?> join : from.getJoins()) {
      if (join.getAttribute().getName().equals(attribute)
          && join.getJoinType().equals(JoinType.INNER)) {
        return true;
      }
    }

    return false;
  }

  static void checkSortExpression(Sort.Order order) {
    if (order instanceof JpaSort.JpaOrder jpaOrder && jpaOrder.isUnsafe()) {
      return;
    }

    if (PUNCTATION_PATTERN.matcher(order.getProperty()).find()) {
      throw new InvalidDataAccessApiUsageException(String.format(UNSAFE_PROPERTY_REFERENCE, order));
    }
  }
}
