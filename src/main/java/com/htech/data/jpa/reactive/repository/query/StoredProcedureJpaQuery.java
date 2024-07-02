package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.StoredProcedureQuery;
import org.hibernate.reactive.stage.Stage;
import org.springframework.data.repository.query.Parameter;
import reactor.core.publisher.Mono;

public class StoredProcedureJpaQuery extends AbstractReactiveJpaQuery {

  private final StoredProcedureAttributes procedureAttributes;
  private final boolean useNamedParameters;
  private final QueryParameterSetter.QueryMetadataCache metadataCache;

  public StoredProcedureJpaQuery(ReactiveJpaQueryMethod method, Stage.SessionFactory sessionFactory) {
    super(method, sessionFactory);
    this.procedureAttributes = method.getProcedureAttributes();
    this.useNamedParameters = useNamedParameters(method);
    this.metadataCache = new QueryParameterSetter.QueryMetadataCache();
  }

  @Override
  protected Mono<Stage.AbstractQuery> doCreateQuery(Mono<Stage.Session> session, ReactiveJpaParametersParameterAccessor accessor, ReactiveJpaQueryMethod method) {
    return procedureAttributes.isNamedStoredProcedure() ? newNamedStoredProcedureQuery(session) : newAdhocStoredProcedureQuery(session);
  }

  private Mono<Stage.AbstractQuery> newAdhocStoredProcedureQuery(Mono<Stage.Session> session) {
    return session.map(s -> s.sto());
  }

  private Mono<Stage.AbstractQuery> newNamedStoredProcedureQuery(Mono<Stage.Session> session) {
    return session.flatMap(s -> {
      ReactiveJpaParameters parameters = getQueryMethod().getParameters();
      StoredProcedureQuery procedureQuery = createAdhocStoredProcedureQuery();
    });
  }

  private StoredProcedureQuery createAdhocStoredProcedureQuery() {
    return null;
  }

  @Override
  protected Mono<Stage.AbstractQuery> doCreateCountQuery(Mono<Stage.Session> session, ReactiveJpaParametersParameterAccessor accessor) {
    return null;
  }

  private boolean useNamedParameters(ReactiveJpaQueryMethod method) {
    return method.getParameters().stream().anyMatch(Parameter::isNamedParameter);
  }
}
