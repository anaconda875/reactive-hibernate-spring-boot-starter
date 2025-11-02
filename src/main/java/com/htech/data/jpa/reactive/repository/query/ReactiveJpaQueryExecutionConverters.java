package com.htech.data.jpa.reactive.repository.query;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.data.repository.util.QueryExecutionConverters;
import org.springframework.data.util.NullableWrapperConverters;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Bao.Ngo
 */
public class ReactiveJpaQueryExecutionConverters {

  private static final Set<WrapperType> WRAPPER_TYPES = new HashSet<>();
  private static final Set<WrapperType> UN_WRAPPER_TYPES = new HashSet<>();
  private static final Set<Function<Object, Object>> UNWRAPPERS = new HashSet<>();
  private static final Set<Class<?>> ALLOWED_PAGEABLE_TYPES = new HashSet<>();
  private static final Map<Class<?>, QueryExecutionConverters.ExecutionAdapter> EXECUTION_ADAPTER =
      new HashMap<>();
  private static final Map<Class<?>, Boolean> supportsCache = new ConcurrentReferenceHashMap<>();
  private static final TypeInformation<Void> VOID_INFORMATION = TypeInformation.of(Void.class);

  private static final GenericConversionService CONVERSION_SERVICE = new GenericConversionService();

  static {
    CONVERSION_SERVICE.addConverter(new ReactiveFlatteningConverter());
    WRAPPER_TYPES.add(WrapperType.singleValue(Mono.class));
    WRAPPER_TYPES.add(WrapperType.singleValue(Uni.class));

    // WRAPPER_TYPES.add(QueryExecutionConverters.WrapperType.singleValue(CompletableFuture.class));

    UN_WRAPPER_TYPES.add(WrapperType.multiValue(Flux.class));
    UN_WRAPPER_TYPES.add(WrapperType.multiValue(Uni.class));

    // UNWRAPPER_TYPES.add(QueryExecutionConverters.WrapperType.singleValue(CompletableFuture.class));

    /*ALLOWED_PAGEABLE_TYPES.add(Slice.class);
    ALLOWED_PAGEABLE_TYPES.add(Page.class);
    ALLOWED_PAGEABLE_TYPES.add(List.class);
    ALLOWED_PAGEABLE_TYPES.add(Window.class);

    WRAPPER_TYPES.add(QueryExecutionConverters.NullableWrapperToCompletableFutureConverter.getWrapperType());

    UNWRAPPERS.addAll(CustomCollections.getUnwrappers());

    CustomCollections.getCustomTypes().stream().map(QueryExecutionConverters.WrapperType::multiValue).forEach(WRAPPER_TYPES::add);

    ALLOWED_PAGEABLE_TYPES.addAll(CustomCollections.getPaginationReturnTypes());*/

  }

  private ReactiveJpaQueryExecutionConverters() {}

  public static boolean supports(Class<?> type) {

    Assert.notNull(type, "Type must not be null");

    return supportsCache.computeIfAbsent(
        type,
        key -> {
          for (WrapperType candidate : WRAPPER_TYPES) {
            if (candidate.getType().isAssignableFrom(key)) {
              return true;
            }
          }

          return NullableWrapperConverters.supports(type);
        });
  }

  public static boolean isSingleValue(Class<?> type) {
    for (WrapperType candidate : WRAPPER_TYPES) {
      if (candidate.getType().isAssignableFrom(type)) {
        return candidate.isSingleValue();
      }
    }

    return false;
  }

  public static GenericConversionService getDefaultConversionService() {
    return CONVERSION_SERVICE;
  }

  public static final class WrapperType {

    private WrapperType(Class<?> type, WrapperType.Cardinality cardinality) {
      this.type = type;
      this.cardinality = cardinality;
    }

    public Class<?> getType() {
      return this.type;
    }

    public WrapperType.Cardinality getCardinality() {
      return cardinality;
    }

    @Override
    public boolean equals(@Nullable Object o) {

      if (this == o) {
        return true;
      }

      if (!(o instanceof WrapperType that)) {
        return false;
      }

      if (!ObjectUtils.nullSafeEquals(type, that.type)) {
        return false;
      }

      return cardinality == that.cardinality;
    }

    @Override
    public int hashCode() {
      int result = ObjectUtils.nullSafeHashCode(type);
      result = 31 * result + ObjectUtils.nullSafeHashCode(cardinality);
      return result;
    }

    @Override
    public String toString() {
      return "QueryExecutionConverters.WrapperType(type="
          + this.getType()
          + ", cardinality="
          + this.getCardinality()
          + ")";
    }

    enum Cardinality {
      NONE,
      SINGLE,
      MULTI;
    }

    private final Class<?> type;
    private final WrapperType.Cardinality cardinality;

    public static WrapperType singleValue(Class<?> type) {
      return new WrapperType(type, WrapperType.Cardinality.SINGLE);
    }

    public static WrapperType multiValue(Class<?> type) {
      return new WrapperType(type, WrapperType.Cardinality.MULTI);
    }

    public static WrapperType noValue(Class<?> type) {
      return new WrapperType(type, WrapperType.Cardinality.NONE);
    }

    boolean isSingleValue() {
      return cardinality.equals(WrapperType.Cardinality.SINGLE);
    }
  }

  static class ReactiveFlatteningConverter implements GenericConverter {

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
      return Set.of(
          /*new ConvertiblePair(Mono.class, Mono.class), */ new ConvertiblePair(
              Flux.class, Flux.class),
          /*new ConvertiblePair(Uni.class, Uni.class), */ new ConvertiblePair(
              Multi.class, Multi.class));
    }

    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
      if (source instanceof Flux<?> flux) {
        return flux.flatMap(
            o -> {
              if (o instanceof Iterable<?> i) {
                return Flux.fromIterable(i);
              }
              if (o instanceof Stream<?> stream) {
                return Flux.fromStream(stream);
              }

              return Mono.just(o);
            });
      }

      if (source instanceof Multi<?> multi) {
        return multi.flatMap(
            o -> {
              if (o instanceof Iterable<?> i) {
                return Multi.createFrom().iterable(i);
              }
              if (o instanceof Stream<?> stream) {
                return Multi.createFrom().items(stream);
              }

              return Multi.createFrom().items(o);
            });
      }

      return source;
    }
  }
}
