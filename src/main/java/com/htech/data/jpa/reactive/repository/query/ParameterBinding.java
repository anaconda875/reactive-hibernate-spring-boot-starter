package com.htech.data.jpa.reactive.repository.query;

import static org.springframework.util.ObjectUtils.nullSafeEquals;
import static org.springframework.util.ObjectUtils.nullSafeHashCode;

import java.lang.reflect.Array;
import java.util.*;

import org.springframework.data.expression.ValueExpression;
import org.springframework.data.jpa.provider.PersistenceProvider;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.util.NullableWrapperConverters;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * @author Bao.Ngo
 */
public class ParameterBinding {

  private final ParameterBinding.BindingIdentifier identifier;
  private final ParameterBinding.ParameterOrigin origin;

  ParameterBinding(
      ParameterBinding.BindingIdentifier identifier, ParameterBinding.ParameterOrigin origin) {

    Assert.notNull(identifier, "BindingIdentifier must not be null");
    Assert.notNull(origin, "ParameterOrigin must not be null");

    this.identifier = identifier;
    this.origin = origin;
  }

  public ParameterBinding.BindingIdentifier getIdentifier() {
    return identifier;
  }

  public ParameterBinding.ParameterOrigin getOrigin() {
    return origin;
  }

  @Nullable
  public String getName() {
    return identifier.hasName() ? identifier.getName() : null;
  }

  String getRequiredName() throws IllegalStateException {

    String name = getName();

    if (name != null) {
      return name;
    }

    throw new IllegalStateException(String.format("Required name for %s not available", this));
  }

  @Nullable
  Integer getPosition() {
    return identifier.hasPosition() ? identifier.getPosition() : null;
  }

  int getRequiredPosition() throws IllegalStateException {

    Integer position = getPosition();

    if (position != null) {
      return position;
    }

    throw new IllegalStateException(String.format("Required position for %s not available", this));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ParameterBinding that = (ParameterBinding) o;

    if (!nullSafeEquals(identifier, that.identifier)) {
      return false;
    }
    return nullSafeEquals(origin, that.origin);
  }

  @Override
  public int hashCode() {
    int result = nullSafeHashCode(identifier);
    result = 31 * result + nullSafeHashCode(origin);
    return result;
  }

  @Override
  public String toString() {
    return String.format("ParameterBinding [identifier: %s, origin: %s]", identifier, origin);
  }

  public Object prepare(Object valueToBind) {
    return valueToBind;
//    if (valueToBind instanceof Optional<?> op) {
//      return (Optional<Object>) op;
//    }
//    return Optional.ofNullable(valueToBind);
  }

  public boolean bindsTo(ParameterBinding other) {
    if (identifier.hasName() && other.identifier.hasName()) {
      if (identifier.getName().equals(other.identifier.getName())) {
        return true;
      }
    }

    if (identifier.hasPosition() && other.identifier.hasPosition()) {
      if (identifier.getPosition() == other.identifier.getPosition()) {
        return true;
      }
    }

    return false;
  }

  public boolean isCompatibleWith(ParameterBinding other) {
    return other.getClass() == getClass() && other.getOrigin().equals(getOrigin());
  }

  static class InParameterBinding extends ParameterBinding {

    InParameterBinding(
        ParameterBinding.BindingIdentifier identifier, ParameterBinding.ParameterOrigin origin) {
      super(identifier, origin);
    }

    @Override
    public Object prepare(Object value) {
      if (NullableWrapperConverters.supports(value.getClass())) {
        value = NullableWrapperConverters.unwrap(value);
      }

      if (!ObjectUtils.isArray(value)) {
        return value;
      }

      int length = Array.getLength(value);
      List<Object> result = new ArrayList<>(length);

      for (int i = 0; i < length; i++) {
        result.add(Array.get(value, i));
      }

      return result;
    }
  }

  static class LikeParameterBinding extends ParameterBinding {

    private static final List<Part.Type> SUPPORTED_TYPES =
        Arrays.asList(
            Part.Type.CONTAINING, Part.Type.STARTING_WITH, Part.Type.ENDING_WITH, Part.Type.LIKE);

    private final Part.Type type;

    LikeParameterBinding(
        ParameterBinding.BindingIdentifier identifier,
        ParameterBinding.ParameterOrigin origin,
        Part.Type type) {

      super(identifier, origin);

      Assert.notNull(type, "Type must not be null");

      Assert.isTrue(
          SUPPORTED_TYPES.contains(type),
          String.format(
              "Type must be one of %s",
              StringUtils.collectionToCommaDelimitedString(SUPPORTED_TYPES)));

      this.type = type;
    }

    public Part.Type getType() {
      return type;
    }

    @Nullable
    @Override
    public Object prepare(Object value) {
      if (NullableWrapperConverters.supports(value.getClass())) {
        value = NullableWrapperConverters.unwrap(value);
      }

      Object unwrapped = PersistenceProvider.unwrapTypedParameterValue(value);
      if (unwrapped == null) {
        return null;
      }

      return switch (type) {
        case STARTING_WITH -> String.format("%s%%", unwrapped);
        case ENDING_WITH -> String.format("%%%s", unwrapped);
        case CONTAINING -> String.format("%%%s%%", unwrapped);
        default -> unwrapped;
      };
    }

    @Override
    public boolean equals(Object obj) {

      if (!(obj instanceof ParameterBinding.LikeParameterBinding)) {
        return false;
      }

      ParameterBinding.LikeParameterBinding that = (ParameterBinding.LikeParameterBinding) obj;

      return super.equals(obj) && this.type.equals(that.type);
    }

    @Override
    public int hashCode() {

      int result = super.hashCode();

      result += nullSafeHashCode(this.type);

      return result;
    }

    @Override
    public String toString() {
      return String.format(
          "LikeBinding [identifier: %s, origin: %s, type: %s]",
          getIdentifier(), getOrigin(), getType());
    }

    @Override
    public boolean isCompatibleWith(ParameterBinding binding) {

      if (super.isCompatibleWith(binding)
          && binding instanceof ParameterBinding.LikeParameterBinding other) {
        return getType() == other.getType();
      }

      return false;
    }

    static Part.Type getLikeTypeFrom(String expression) {

      Assert.hasText(expression, "Expression must not be null or empty");

      if (expression.matches("%.*%")) {
        return Part.Type.CONTAINING;
      }

      if (expression.startsWith("%")) {
        return Part.Type.ENDING_WITH;
      }

      if (expression.endsWith("%")) {
        return Part.Type.STARTING_WITH;
      }

      return Part.Type.LIKE;
    }
  }

  sealed interface BindingIdentifier
      permits ParameterBinding.Named, ParameterBinding.Indexed, ParameterBinding.NamedAndIndexed {

    static ParameterBinding.BindingIdentifier of(String name) {

      Assert.hasText(name, "Name must not be empty");

      return new ParameterBinding.Named(name);
    }

    static ParameterBinding.BindingIdentifier of(int position) {

      Assert.isTrue(position > 0, "Index position must be greater zero");

      return new ParameterBinding.Indexed(position);
    }

    static ParameterBinding.BindingIdentifier of(String name, int position) {

      Assert.hasText(name, "Name must not be empty");

      return new ParameterBinding.NamedAndIndexed(name, position);
    }

    default boolean hasName() {
      return false;
    }

    default boolean hasPosition() {
      return false;
    }

    default String getName() {
      throw new IllegalStateException("No name associated");
    }

    default int getPosition() {
      throw new IllegalStateException("No position associated");
    }
  }

  private record Named(String name) implements ParameterBinding.BindingIdentifier {

    @Override
    public boolean hasName() {
      return true;
    }

    @Override
    public String getName() {
      return name();
    }

    @Override
    public String toString() {
      return name();
    }
  }

  private record Indexed(int position) implements ParameterBinding.BindingIdentifier {

    @Override
    public boolean hasPosition() {
      return true;
    }

    @Override
    public int getPosition() {
      return position();
    }

    @Override
    public String toString() {
      return "[" + position() + "]";
    }
  }

  private record NamedAndIndexed(String name, int position)
      implements ParameterBinding.BindingIdentifier {

    @Override
    public boolean hasName() {
      return true;
    }

    @Override
    public String getName() {
      return name();
    }

    @Override
    public boolean hasPosition() {
      return true;
    }

    @Override
    public int getPosition() {
      return position();
    }

    @Override
    public String toString() {
      return "[" + name() + ", " + position() + "]";
    }
  }

  sealed interface ParameterOrigin
      permits ParameterBinding.Expression, ParameterBinding.MethodInvocationArgument {

    static ParameterBinding.Expression ofExpression(ValueExpression expression) {
      return new ParameterBinding.Expression(expression);
    }

    static ParameterBinding.MethodInvocationArgument ofParameter(
        @Nullable String name, @Nullable Integer position) {

      ParameterBinding.BindingIdentifier identifier;
      if (!ObjectUtils.isEmpty(name) && position != null) {
        identifier = ParameterBinding.BindingIdentifier.of(name, position);
      } else if (!ObjectUtils.isEmpty(name)) {
        identifier = ParameterBinding.BindingIdentifier.of(name);
      } else {
        identifier = ParameterBinding.BindingIdentifier.of(position);
      }

      return ofParameter(identifier);
    }

    static ParameterBinding.MethodInvocationArgument ofParameter(int position) {
      return ofParameter(ParameterBinding.BindingIdentifier.of(position));
    }

    static ParameterBinding.MethodInvocationArgument ofParameter(
        ParameterBinding.BindingIdentifier identifier) {
      return new ParameterBinding.MethodInvocationArgument(identifier);
    }

    boolean isMethodArgument();

    boolean isExpression();
  }

  public record Expression(ValueExpression expression) implements ParameterBinding.ParameterOrigin {

    @Override
    public boolean isMethodArgument() {
      return false;
    }

    @Override
    public boolean isExpression() {
      return true;
    }
  }

  public record MethodInvocationArgument(ParameterBinding.BindingIdentifier identifier)
      implements ParameterBinding.ParameterOrigin {

    @Override
    public boolean isMethodArgument() {
      return true;
    }

    @Override
    public boolean isExpression() {
      return false;
    }
  }
}
