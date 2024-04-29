package com.htech.data.jpa.reactive.repository.query;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.repository.query.SpelQueryContext;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.lang.Nullable;
import org.springframework.util.*;

public class StringQuery implements DeclaredQuery {

  protected final String query;
  protected final List<ParameterBinding> bindings;
  protected final @Nullable String alias;
  protected final boolean hasConstructorExpression;
  protected final boolean containsPageableInSpel;
  private final boolean usesJdbcStyleParameters;
  protected final boolean isNative;
  protected final QueryEnhancer queryEnhancer;

  StringQuery(String query, boolean isNative) {

    Assert.hasText(query, "Query must not be null or empty");

    this.isNative = isNative;
    this.bindings = new ArrayList<>();
    this.containsPageableInSpel = query.contains("#pageable");

    StringQuery.Metadata queryMeta = new StringQuery.Metadata();
    this.query =
        StringQuery.ParameterBindingParser.INSTANCE
            .parseParameterBindingsOfQueryIntoBindingsAndReturnCleanedQuery(
                query, this.bindings, queryMeta);

    this.usesJdbcStyleParameters = queryMeta.usesJdbcStyleParameters;

    this.queryEnhancer = QueryEnhancerFactory.forQuery(this);
    this.alias = this.queryEnhancer.detectAlias();
    this.hasConstructorExpression = this.queryEnhancer.hasConstructorExpression();
  }

  /** Returns whether we have found some like bindings. */
  boolean hasParameterBindings() {
    return !bindings.isEmpty();
  }

  String getProjection() {
    return this.queryEnhancer.getProjection();
  }

  @Override
  public List<ParameterBinding> getParameterBindings() {
    return bindings;
  }

  @Override
  public DeclaredQuery deriveCountQuery(
      @Nullable String countQuery, @Nullable String countQueryProjection) {

    return DeclaredQuery.of( //
        countQuery != null
            ? countQuery
            : this.queryEnhancer.createCountQueryFor(countQueryProjection), //
        this.isNative);
  }

  @Override
  public String getQueryString() {
    return query;
  }

  @Override
  @Nullable
  public String getAlias() {
    return alias;
  }

  @Override
  public boolean hasConstructorExpression() {
    return hasConstructorExpression;
  }

  @Override
  public boolean isDefaultProjection() {
    return getProjection().equalsIgnoreCase(alias);
  }

  @Override
  public boolean hasNamedParameter() {
    return bindings.stream().anyMatch(b -> b.getIdentifier().hasName());
  }

  @Override
  public boolean usesPaging() {
    return containsPageableInSpel;
  }

  @Override
  public boolean isNativeQuery() {
    return isNative;
  }

  @Override
  public boolean usesJdbcStyleParameters() {
    return usesJdbcStyleParameters;
  }

  /**
   * A parser that extracts the parameter bindings from a given query string.
   *
   * @author Thomas Darimont
   */
  enum ParameterBindingParser {
    INSTANCE;

    private static final String EXPRESSION_PARAMETER_PREFIX = "__$synthetic$__";
    public static final String POSITIONAL_OR_INDEXED_PARAMETER = "\\?(\\d*+(?![#\\w]))";
    // .....................................................................^ not followed by a hash
    // or a letter.
    // .................................................................^ zero or more digits.
    // .............................................................^ start with a question mark.
    private static final Pattern PARAMETER_BINDING_BY_INDEX =
        Pattern.compile(POSITIONAL_OR_INDEXED_PARAMETER);
    private static final Pattern PARAMETER_BINDING_PATTERN;
    private static final Pattern JDBC_STYLE_PARAM =
        Pattern.compile("(?!\\\\)\\?(?!\\d)"); // no \ and [no digit]
    private static final Pattern NUMBERED_STYLE_PARAM =
        Pattern.compile("(?!\\\\)\\?\\d"); // no \ and [digit]
    private static final Pattern NAMED_STYLE_PARAM =
        Pattern.compile("(?!\\\\):\\w+"); // no \ and :[text]

    private static final String MESSAGE =
        "Already found parameter binding with same index / parameter name but differing binding type; "
            + "Already have: %s, found %s; If you bind a parameter multiple times make sure they use the same binding";
    private static final int INDEXED_PARAMETER_GROUP = 4;
    private static final int NAMED_PARAMETER_GROUP = 6;
    private static final int COMPARISION_TYPE_GROUP = 1;

    static {
      List<String> keywords = new ArrayList<>();

      for (StringQuery.ParameterBindingParser.ParameterBindingType type :
          StringQuery.ParameterBindingParser.ParameterBindingType.values()) {
        if (type.getKeyword() != null) {
          keywords.add(type.getKeyword());
        }
      }

      StringBuilder builder = new StringBuilder();
      builder.append("(");
      builder.append(StringUtils.collectionToDelimitedString(keywords, "|")); // keywords
      builder.append(")?");
      builder.append("(?: )?"); // some whitespace
      builder.append("\\(?"); // optional braces around parameters
      builder.append("(");
      builder.append(
          "%?("
              + POSITIONAL_OR_INDEXED_PARAMETER
              + ")%?"); // position parameter and parameter index
      builder.append("|"); // or

      // named parameter and the parameter name
      builder.append(
          "%?(" + QueryUtils.COLON_NO_DOUBLE_COLON + QueryUtils.IDENTIFIER_GROUP + ")%?");

      builder.append(")");
      builder.append("\\)?"); // optional braces around parameters

      PARAMETER_BINDING_PATTERN = Pattern.compile(builder.toString(), CASE_INSENSITIVE);
    }

    /**
     * Parses {@link ParameterBinding} instances from the given query and adds them to the
     * registered bindings. Returns the cleaned up query.
     */
    private String parseParameterBindingsOfQueryIntoBindingsAndReturnCleanedQuery(
        String query, List<ParameterBinding> bindings, StringQuery.Metadata queryMeta) {

      int greatestParameterIndex = tryFindGreatestParameterIndexIn(query);
      boolean parametersShouldBeAccessedByIndex = greatestParameterIndex != -1;

      /*
       * Prefer indexed access over named parameters if only SpEL Expression parameters are present.
       */
      if (!parametersShouldBeAccessedByIndex && query.contains("?#{")) {
        parametersShouldBeAccessedByIndex = true;
        greatestParameterIndex = 0;
      }

      SpelQueryContext.SpelExtractor spelExtractor =
          createSpelExtractor(query, parametersShouldBeAccessedByIndex, greatestParameterIndex);

      String resultingQuery = spelExtractor.getQueryString();
      Matcher matcher = PARAMETER_BINDING_PATTERN.matcher(resultingQuery);

      int expressionParameterIndex = parametersShouldBeAccessedByIndex ? greatestParameterIndex : 0;
      int syntheticParameterIndex = expressionParameterIndex + spelExtractor.size();

      StringQuery.ParameterBindings parameterBindings =
          new StringQuery.ParameterBindings(
              bindings, it -> checkAndRegister(it, bindings), syntheticParameterIndex);
      int currentIndex = 0;

      boolean usesJpaStyleParameters = false;

      while (matcher.find()) {

        if (spelExtractor.isQuoted(matcher.start())) {
          continue;
        }

        String parameterIndexString = matcher.group(INDEXED_PARAMETER_GROUP);
        String parameterName =
            parameterIndexString != null ? null : matcher.group(NAMED_PARAMETER_GROUP);
        Integer parameterIndex = getParameterIndex(parameterIndexString);

        String match = matcher.group(0);
        if (JDBC_STYLE_PARAM.matcher(match).find()) {
          queryMeta.usesJdbcStyleParameters = true;
        }

        if (NUMBERED_STYLE_PARAM.matcher(match).find() || NAMED_STYLE_PARAM.matcher(match).find()) {
          usesJpaStyleParameters = true;
        }

        if (usesJpaStyleParameters && queryMeta.usesJdbcStyleParameters) {
          throw new IllegalArgumentException(
              "Mixing of ? parameters and other forms like ?1 is not supported");
        }

        String typeSource = matcher.group(COMPARISION_TYPE_GROUP);
        Assert.isTrue(
            parameterIndexString != null || parameterName != null,
            () ->
                String.format(
                    "We need either a name or an index; Offending query string: %s", query));
        String expression =
            spelExtractor.getParameter(
                parameterName == null ? parameterIndexString : parameterName);
        String replacement = null;

        expressionParameterIndex++;
        if ("".equals(parameterIndexString)) {
          parameterIndex = expressionParameterIndex;
        }

        ParameterBinding.BindingIdentifier queryParameter;
        if (parameterIndex != null) {
          queryParameter = ParameterBinding.BindingIdentifier.of(parameterIndex);
        } else {
          queryParameter = ParameterBinding.BindingIdentifier.of(parameterName);
        }
        ParameterBinding.ParameterOrigin origin =
            ObjectUtils.isEmpty(expression)
                ? ParameterBinding.ParameterOrigin.ofParameter(parameterName, parameterIndex)
                : ParameterBinding.ParameterOrigin.ofExpression(expression);

        ParameterBinding.BindingIdentifier targetBinding = queryParameter;
        Function<ParameterBinding.BindingIdentifier, ParameterBinding> bindingFactory;
        switch (StringQuery.ParameterBindingParser.ParameterBindingType.of(typeSource)) {
          case LIKE:
            Part.Type likeType =
                ParameterBinding.LikeParameterBinding.getLikeTypeFrom(matcher.group(2));
            bindingFactory =
                (identifier) ->
                    new ParameterBinding.LikeParameterBinding(identifier, origin, likeType);
            break;

          case IN:
            bindingFactory =
                (identifier) -> new ParameterBinding.InParameterBinding(identifier, origin);
            break;

          case AS_IS: // fall-through we don't need a special parameter queryParameter for the given
            // parameter.
          default:
            bindingFactory = (identifier) -> new ParameterBinding(identifier, origin);
        }

        if (origin.isExpression()) {
          parameterBindings.register(bindingFactory.apply(queryParameter));
        } else {
          targetBinding = parameterBindings.register(queryParameter, origin, bindingFactory);
        }

        replacement =
            targetBinding.hasName()
                ? ":" + targetBinding.getName()
                : ((!usesJpaStyleParameters && queryMeta.usesJdbcStyleParameters)
                    ? "?"
                    : "?" + targetBinding.getPosition());
        String result;
        String substring = matcher.group(2);

        int index = resultingQuery.indexOf(substring, currentIndex);
        if (index < 0) {
          result = resultingQuery;
        } else {
          currentIndex = index + replacement.length();
          result =
              resultingQuery.substring(0, index)
                  + replacement
                  + resultingQuery.substring(index + substring.length());
        }

        resultingQuery = result;
      }

      return resultingQuery;
    }

    private static SpelQueryContext.SpelExtractor createSpelExtractor(
        String queryWithSpel,
        boolean parametersShouldBeAccessedByIndex,
        int greatestParameterIndex) {

      /*
       * If parameters need to be bound by index, we bind the synthetic expression parameters starting from position of the greatest discovered index parameter in order to
       * not mix-up with the actual parameter indices.
       */
      int expressionParameterIndex = parametersShouldBeAccessedByIndex ? greatestParameterIndex : 0;

      BiFunction<Integer, String, String> indexToParameterName =
          parametersShouldBeAccessedByIndex
              ? (index, expression) -> String.valueOf(index + expressionParameterIndex + 1)
              : (index, expression) -> EXPRESSION_PARAMETER_PREFIX + (index + 1);

      String fixedPrefix = parametersShouldBeAccessedByIndex ? "?" : ":";

      BiFunction<String, String, String> parameterNameToReplacement =
          (prefix, name) -> fixedPrefix + name;

      return SpelQueryContext.of(indexToParameterName, parameterNameToReplacement)
          .parse(queryWithSpel);
    }

    @Nullable
    private static Integer getParameterIndex(@Nullable String parameterIndexString) {

      if (parameterIndexString == null || parameterIndexString.isEmpty()) {
        return null;
      }
      return Integer.valueOf(parameterIndexString);
    }

    private static int tryFindGreatestParameterIndexIn(String query) {

      Matcher parameterIndexMatcher = PARAMETER_BINDING_BY_INDEX.matcher(query);

      int greatestParameterIndex = -1;
      while (parameterIndexMatcher.find()) {

        String parameterIndexString = parameterIndexMatcher.group(1);
        Integer parameterIndex = getParameterIndex(parameterIndexString);
        if (parameterIndex != null) {
          greatestParameterIndex = Math.max(greatestParameterIndex, parameterIndex);
        }
      }

      return greatestParameterIndex;
    }

    private static void checkAndRegister(
        ParameterBinding binding, List<ParameterBinding> bindings) {

      bindings.stream() //
          .filter(it -> it.bindsTo(binding)) //
          .forEach(it -> Assert.isTrue(it.equals(binding), String.format(MESSAGE, it, binding)));

      if (!bindings.contains(binding)) {
        bindings.add(binding);
      }
    }

    /**
     * An enum for the different types of bindings.
     *
     * @author Thomas Darimont
     * @author Oliver Gierke
     */
    private enum ParameterBindingType {

      // Trailing whitespace is intentional to reflect that the keywords must be used with at least
      // one whitespace
      // character, while = does not.
      LIKE("like "),
      IN("in "),
      AS_IS(null);

      private final @Nullable String keyword;

      ParameterBindingType(@Nullable String keyword) {
        this.keyword = keyword;
      }

      /**
       * Returns the keyword that will trigger the binding type or {@literal null} if the type is
       * not triggered by a keyword.
       *
       * @return the keyword
       */
      @Nullable
      public String getKeyword() {
        return keyword;
      }

      /**
       * Return the appropriate {@link StringQuery.ParameterBindingParser.ParameterBindingType} for
       * the given {@link String}. Returns {@literal #AS_IS} in case no other {@link
       * StringQuery.ParameterBindingParser.ParameterBindingType} could be found.
       */
      static StringQuery.ParameterBindingParser.ParameterBindingType of(String typeSource) {

        if (!StringUtils.hasText(typeSource)) {
          return AS_IS;
        }

        for (StringQuery.ParameterBindingParser.ParameterBindingType type : values()) {
          if (type.name().equalsIgnoreCase(typeSource.trim())) {
            return type;
          }
        }

        throw new IllegalArgumentException(
            String.format("Unsupported parameter binding type %s", typeSource));
      }
    }
  }

  private static class Metadata {
    private boolean usesJdbcStyleParameters = false;
  }

  /**
   * Utility to create unique parameter bindings for LIKE that refer to the same underlying method
   * parameter but are bound to potentially unique query parameters for {@link
   * ParameterBinding.LikeParameterBinding#prepare(Object) LIKE rewrite}.
   *
   * @author Mark Paluch
   * @since 3.1.2
   */
  static class ParameterBindings {

    private final MultiValueMap<ParameterBinding.BindingIdentifier, ParameterBinding>
        methodArgumentToLikeBindings = new LinkedMultiValueMap<>();

    private final Consumer<ParameterBinding> registration;
    private int syntheticParameterIndex;

    public ParameterBindings(
        List<ParameterBinding> bindings,
        Consumer<ParameterBinding> registration,
        int syntheticParameterIndex) {

      for (ParameterBinding binding : bindings) {
        this.methodArgumentToLikeBindings.put(
            binding.getIdentifier(), new ArrayList<>(List.of(binding)));
      }

      this.registration = registration;
      this.syntheticParameterIndex = syntheticParameterIndex;
    }

    /**
     * Return whether the identifier is already bound.
     *
     * @param identifier
     * @return
     */
    public boolean isBound(ParameterBinding.BindingIdentifier identifier) {
      return !getBindings(identifier).isEmpty();
    }

    ParameterBinding.BindingIdentifier register(
        ParameterBinding.BindingIdentifier identifier,
        ParameterBinding.ParameterOrigin origin,
        Function<ParameterBinding.BindingIdentifier, ParameterBinding> bindingFactory) {

      Assert.isInstanceOf(ParameterBinding.MethodInvocationArgument.class, origin);

      ParameterBinding.BindingIdentifier methodArgument =
          ((ParameterBinding.MethodInvocationArgument) origin).identifier();
      List<ParameterBinding> bindingsForOrigin = getBindings(methodArgument);

      if (!isBound(identifier)) {

        ParameterBinding binding = bindingFactory.apply(identifier);
        registration.accept(binding);
        bindingsForOrigin.add(binding);
        return binding.getIdentifier();
      }

      ParameterBinding binding = bindingFactory.apply(identifier);

      for (ParameterBinding existing : bindingsForOrigin) {

        if (existing.isCompatibleWith(binding)) {
          return existing.getIdentifier();
        }
      }

      ParameterBinding.BindingIdentifier syntheticIdentifier;
      if (identifier.hasName() && methodArgument.hasName()) {

        int index = 0;
        String newName = methodArgument.getName();
        while (existsBoundParameter(newName)) {
          index++;
          newName = methodArgument.getName() + "_" + index;
        }
        syntheticIdentifier = ParameterBinding.BindingIdentifier.of(newName);
      } else {
        syntheticIdentifier = ParameterBinding.BindingIdentifier.of(++syntheticParameterIndex);
      }

      ParameterBinding newBinding = bindingFactory.apply(syntheticIdentifier);
      registration.accept(newBinding);
      bindingsForOrigin.add(newBinding);
      return newBinding.getIdentifier();
    }

    private boolean existsBoundParameter(String key) {
      return methodArgumentToLikeBindings.values().stream()
          .flatMap(Collection::stream)
          .anyMatch(it -> key.equals(it.getName()));
    }

    private List<ParameterBinding> getBindings(ParameterBinding.BindingIdentifier identifier) {
      return methodArgumentToLikeBindings.computeIfAbsent(identifier, s -> new ArrayList<>());
    }

    public void register(ParameterBinding parameterBinding) {
      registration.accept(parameterBinding);
    }
  }
}
