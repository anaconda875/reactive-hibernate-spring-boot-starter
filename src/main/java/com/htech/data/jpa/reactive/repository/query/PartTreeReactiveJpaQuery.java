package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import java.util.List;
import org.hibernate.reactive.stage.Stage;
import org.springframework.data.domain.OffsetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.query.*;
import org.springframework.data.jpa.repository.support.JpaMetamodelEntityInformation;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.data.util.Streamable;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

public class PartTreeReactiveJpaQuery extends AbstractReactiveJpaQuery {

  private final PartTree tree;
  private final ReactiveJpaParameters parameters;

  private final QueryPreparer query;
  private final QueryPreparer countQuery;
  private final EscapeCharacter escape;
  private final JpaMetamodelEntityInformation<?, Object> entityInformation;

  PartTreeReactiveJpaQuery(
      ReactiveJpaQueryMethod method,
      EntityManagerFactory entityManagerFactory,
      Stage.SessionFactory sessionFactory) {
    this(method, entityManagerFactory, sessionFactory, EscapeCharacter.DEFAULT);
  }

  PartTreeReactiveJpaQuery(
      ReactiveJpaQueryMethod method,
      EntityManagerFactory entityManagerFactory,
      Stage.SessionFactory sessionFactory,
      EscapeCharacter escape) {

    super(method, sessionFactory);

    this.escape = escape;
    this.parameters = method.getParameters();

    Class<?> domainClass = method.getEntityInformation().getJavaType();
    PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
    this.entityInformation =
        new JpaMetamodelEntityInformation<>(
            domainClass, sessionFactory.getMetamodel(), persistenceUnitUtil);

    boolean recreationRequired =
        parameters.hasDynamicProjection()
            || parameters.potentiallySortsDynamically()
            || method.isScrollQuery();

    try {

      this.tree = new PartTree(method.getName(), domainClass);
      validate(tree, parameters, method.toString());
      this.countQuery = new CountQueryPreparer(recreationRequired);
      this.query =
          tree.isCountProjection()
              ? countQuery
              : tree.isDelete()
                  ? new DeleteQueryPreparer(recreationRequired)
                  : new SelectQueryPreparer(recreationRequired);

    } catch (Exception o_O) {
      throw new IllegalArgumentException(
          String.format("Failed to create query for method %s; %s", method, o_O.getMessage()), o_O);
    }
  }

  @Override
  public Mono<Stage.AbstractQuery> doCreateQuery(
      Mono<Stage.Session> session,
      ReactiveJpaParametersParameterAccessor accessor,
      ReactiveJpaQueryMethod method) {
    return query.createQuery(session, accessor);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Mono<Stage.AbstractQuery> doCreateCountQuery(
      Mono<Stage.Session> session, ReactiveJpaParametersParameterAccessor accessor) {
    return /*(Stage.SelectionQuery<Long>) */ countQuery.createQuery(session, accessor);
  }

  @Override
  protected ReactiveJpaQueryExecution getExecution() {
    // TODO
    if (this.tree.isDelete()) {
      return new ReactiveJpaQueryExecution.DeleteExecution(sessionFactory);
    } else if (this.tree.isExistsProjection()) {
      return new ReactiveJpaQueryExecution.ExistsExecution();
    }

    return super.getExecution();
  }

  private static void validate(PartTree tree, ReactiveJpaParameters parameters, String methodName) {

    int argCount = 0;

    Iterable<Part> parts = () -> tree.stream().flatMap(Streamable::stream).iterator();

    for (Part part : parts) {

      int numberOfArguments = part.getNumberOfArguments();

      for (int i = 0; i < numberOfArguments; i++) {

        throwExceptionOnArgumentMismatch(methodName, part, parameters, argCount);

        argCount++;
      }
    }
  }

  private static void throwExceptionOnArgumentMismatch(
      String methodName, Part part, ReactiveJpaParameters parameters, int index) {

    Part.Type type = part.getType();
    String property = part.getProperty().toDotPath();

    if (!parameters.getBindableParameters().hasParameterAt(index)) {
      throw new IllegalStateException(
          String.format(
              "Method %s expects at least %d arguments but only found %d; This leaves an operator of type %s for property %s unbound",
              methodName, index + 1, index, type.name(), property));
    }

    ReactiveJpaParameters.JpaParameter parameter = parameters.getBindableParameter(index);

    if (expectsCollection(type) && !parameterIsCollectionLike(parameter)) {
      throw new IllegalStateException(
          wrongParameterTypeMessage(methodName, property, type, "Collection", parameter));
    } else if (!expectsCollection(type) && !parameterIsScalarLike(parameter)) {
      throw new IllegalStateException(
          wrongParameterTypeMessage(methodName, property, type, "scalar", parameter));
    }
  }

  private static String wrongParameterTypeMessage(
      String methodName,
      String property,
      Part.Type operatorType,
      String expectedArgumentType,
      ReactiveJpaParameters.JpaParameter parameter) {

    return String.format(
        "Operator %s on %s requires a %s argument, found %s in method %s",
        operatorType.name(), property, expectedArgumentType, parameter.getType(), methodName);
  }

  private static boolean parameterIsCollectionLike(ReactiveJpaParameters.JpaParameter parameter) {
    return Iterable.class.isAssignableFrom(parameter.getType()) || parameter.getType().isArray();
  }

  private static boolean parameterIsScalarLike(ReactiveJpaParameters.JpaParameter parameter) {
    return !Iterable.class.isAssignableFrom(parameter.getType());
  }

  private static boolean expectsCollection(Part.Type type) {
    return type == Part.Type.IN || type == Part.Type.NOT_IN;
  }

  abstract class QueryPreparer<C extends CommonAbstractCriteria> {

    protected final @Nullable C cachedCriteria;
    protected final @Nullable ParameterBinder cachedParameterBinder;
    protected final QueryParameterSetter.QueryMetadataCache metadataCache =
        new QueryParameterSetter.QueryMetadataCache();

    QueryPreparer(boolean recreateQueries) {

      AbstractQueryCreator<C, Predicate> creator = createCreator(null);

      if (recreateQueries) {
        this.cachedCriteria = null;
        this.cachedParameterBinder = null;
      } else {
        this.cachedCriteria = creator.createQuery();
        this.cachedParameterBinder = getBinder(creator.getParameterExpressions());
      }
    }

    public Mono<Stage.AbstractQuery> createQuery(
        Mono<Stage.Session> session, ReactiveJpaParametersParameterAccessor accessor) {
      //      C criteriaQuery = cachedCriteria;
      //      ParameterBinder parameterBinder = cachedParameterBinder;

      return Mono.just(accessor)
          .zipWhen(
              a -> {
                C criteriaQuery = cachedCriteria;
                ParameterBinder parameterBinder = cachedParameterBinder;
                if (cachedCriteria == null || accessor.hasBindableNullValue()) {
                  AbstractQueryCreator<C, ?> creator = createCreator(accessor);
                  criteriaQuery = creator.createQuery(getDynamicSort(accessor));
                  List<ParameterMetadataProvider.ParameterMetadata<?>> expressions =
                      creator.getParameterExpressions();
                  parameterBinder = getBinder(expressions);
                }

                if (parameterBinder == null) {
                  return Mono.error(() -> new IllegalStateException("ParameterBinder is null"));
                }

                return Mono.zip(session, Mono.just(criteriaQuery), Mono.just(parameterBinder));
              })
          .map(
              tuple ->
                  Tuples.of(
                      tuple.getT1(),
                      createQuery(tuple.getT2().getT1(), tuple.getT2().getT2()),
                      tuple.getT2().getT3()))
          .flatMap(
              tuple -> {
                ReactiveJpaParametersParameterAccessor acs = tuple.getT1();
                ScrollPosition scrollPosition =
                    acs.getParameters().hasScrollPositionParameter()
                        ? acs.getScrollPosition()
                        : null;
                return invokeBinding(tuple.getT3(), tuple.getT2(), acs, this.metadataCache)
                    .map(query -> restrictMaxResultsIfNecessary(query, scrollPosition));
              });

      //      if (cachedCriteria == null || accessor.hasBindableNullValue()) {
      //        AbstractQueryCreator<C, ?> creator = createCreator(accessor);
      //        criteriaQuery = creator.createQuery(getDynamicSort(accessor));
      //        List<ParameterMetadataProvider.ParameterMetadata<?>> expressions =
      //            creator.getParameterExpressions();
      //        parameterBinder = getBinder(expressions);
      //      }
      //
      //      if (parameterBinder == null) {
      //        throw new IllegalStateException("ParameterBinder is null");
      //      }
      //
      //      // TODO
      //      Stage.AbstractQuery query = createQuery(session, criteriaQuery);
      //
      //      ScrollPosition scrollPosition =
      //          accessor.getParameters().hasScrollPositionParameter()
      //              ? accessor.getScrollPosition()
      //              : null;
      //      return restrictMaxResultsIfNecessary(
      //          invokeBinding(parameterBinder, query, accessor, this.metadataCache),
      // scrollPosition);
    }

    @SuppressWarnings("ConstantConditions")
    protected Stage.AbstractQuery restrictMaxResultsIfNecessary(
        Stage.AbstractQuery query, @Nullable ScrollPosition scrollPosition) {
      Stage.SelectionQuery tmp = (Stage.SelectionQuery) query;
      if (scrollPosition instanceof OffsetScrollPosition offset) {
        tmp.setFirstResult(Math.toIntExact(offset.getOffset()));
      }

      if (tree.isLimiting()) {

        if (tmp.getMaxResults() != Integer.MAX_VALUE) {
          /*
           * In order to return the correct results, we have to adjust the first result offset to be returned if:
           * - a Pageable parameter is present
           * - AND the requested page number > 0
           * - AND the requested page size was bigger than the derived result limitation via the First/Top keyword.
           */
          if (tmp.getMaxResults() > tree.getMaxResults() && tmp.getFirstResult() > 0) {
            tmp.setFirstResult(tmp.getFirstResult() - (tmp.getMaxResults() - tree.getMaxResults()));
          }
        }

        tmp.setMaxResults(tree.getMaxResults());
      }

      if (tree.isExistsProjection()) {
        tmp.setMaxResults(1);
      }

      return tmp;
    }

    protected abstract Stage.AbstractQuery createQuery(Stage.Session session, C criteria);

    protected AbstractQueryCreator<C, Predicate> createCreator(
        @Nullable JpaParametersParameterAccessor accessor) {
      CriteriaBuilder builder = sessionFactory.getCriteriaBuilder();
      ResultProcessor processor = getQueryMethod().getResultProcessor();

      ParameterMetadataProvider provider;
      ReturnedType returnedType;

      if (accessor != null) {
        provider = new ParameterMetadataProvider(builder, accessor, escape);
        returnedType = processor.withDynamicProjection(accessor).getReturnedType();
      } else {
        provider = new ParameterMetadataProvider(builder, parameters, escape);
        returnedType = processor.getReturnedType();
      }

      return (AbstractQueryCreator<C, Predicate>)
          new ReactiveJpaCriteriaQueryCreator(tree, returnedType, builder, provider);
    }

    protected Mono<Stage.AbstractQuery> invokeBinding(
        ParameterBinder binder,
        Stage.AbstractQuery query,
        JpaParametersParameterAccessor accessor,
        QueryParameterSetter.QueryMetadataCache metadataCache) {

      QueryParameterSetter.QueryMetadata metadata = metadataCache.getMetadata("query", query);

      return binder.bindAndPrepare(query, metadata, accessor);
    }

    private ParameterBinder getBinder(
        List<ParameterMetadataProvider.ParameterMetadata<?>> expressions) {
      return ParameterBinderFactory.createCriteriaBinder(parameters, expressions);
    }

    private Sort getDynamicSort(JpaParametersParameterAccessor accessor) {

      return parameters.potentiallySortsDynamically() //
          ? accessor.getSort() //
          : Sort.unsorted();
    }
  }

  private class CountQueryPreparer extends QueryPreparer<CriteriaQuery<?>> {

    CountQueryPreparer(boolean recreateQueries) {
      super(recreateQueries);
    }

    @Override
    protected Stage.AbstractQuery createQuery(Stage.Session session, CriteriaQuery<?> criteria) {
      if (this.cachedCriteria != null) {
        synchronized (this.cachedCriteria) {
          return session.createQuery(criteria);
        }
      }

      return session.createQuery(criteria);
    }

    @Override
    protected ReactiveJpaCountQueryCreator createCreator(
        @Nullable JpaParametersParameterAccessor accessor) {
      CriteriaBuilder builder = sessionFactory.getCriteriaBuilder();

      ParameterMetadataProvider provider;

      if (accessor != null) {
        provider = new ParameterMetadataProvider(builder, accessor, escape);
      } else {
        provider = new ParameterMetadataProvider(builder, parameters, escape);
      }

      return new ReactiveJpaCountQueryCreator(
          tree, getQueryMethod().getResultProcessor().getReturnedType(), builder, provider);
    }

    @Override
    protected Mono<Stage.AbstractQuery> invokeBinding(
        ParameterBinder binder,
        Stage.AbstractQuery query,
        JpaParametersParameterAccessor accessor,
        QueryParameterSetter.QueryMetadataCache metadataCache) {

      QueryParameterSetter.QueryMetadata metadata = metadataCache.getMetadata("countquery", query);

      return binder.bind(query, metadata, accessor);
    }
  }

  private class SelectQueryPreparer extends QueryPreparer<CriteriaQuery<?>> {

    SelectQueryPreparer(boolean recreateQueries) {
      super(recreateQueries);
    }

    @Override
    protected Stage.AbstractQuery createQuery(Stage.Session session, CriteriaQuery<?> criteria) {
      if (this.cachedCriteria != null) {
        synchronized (this.cachedCriteria) {
          return session.createQuery(criteria);
        }
      }

      return session.createQuery(criteria);
    }
  }

  private class DeleteQueryPreparer extends QueryPreparer<CriteriaDelete<?>> {

    DeleteQueryPreparer(boolean recreateQueries) {
      super(recreateQueries);
    }

    @Override
    protected ReactiveJpaCriteriaDeleteQueryCreator createCreator(
        @Nullable JpaParametersParameterAccessor accessor) {
      CriteriaBuilder builder = sessionFactory.getCriteriaBuilder();

      ParameterMetadataProvider provider;

      if (accessor != null) {
        provider = new ParameterMetadataProvider(builder, accessor, escape);
      } else {
        provider = new ParameterMetadataProvider(builder, parameters, escape);
      }

      return new ReactiveJpaCriteriaDeleteQueryCreator(
          tree, getQueryMethod().getResultProcessor().getReturnedType(), builder, provider);
    }

    @Override
    protected Stage.AbstractQuery createQuery(
        Stage.Session session, CriteriaDelete<?> criteriaQuery) {
      if (this.cachedCriteria != null) {
        synchronized (this.cachedCriteria) {
          return session.createQuery(criteriaQuery);
        }
      }

      return session.createQuery(criteriaQuery);
    }

    @Override
    protected Stage.AbstractQuery restrictMaxResultsIfNecessary(
        Stage.AbstractQuery query, ScrollPosition scrollPosition) {
      return query;
    }
  }
}
