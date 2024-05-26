package com.htech.data.jpa.reactive.repository.support;

import static org.springframework.data.repository.util.ReactiveWrapperConverters.toWrapper;
import static org.springframework.transaction.reactive.TransactionSynchronizationManager.forCurrentTransaction;

import com.htech.data.jpa.reactive.core.StageReactiveJpaEntityOperations;
import com.htech.data.jpa.reactive.repository.query.DefaultReactiveJpaQueryExtractor;
import com.htech.data.jpa.reactive.repository.query.ReactiveJpaQueryMethodFactory;
import com.htech.data.jpa.reactive.repository.query.ReactiveQueryRewriterProvider;
import com.htech.jpa.reactive.connection.ConnectionHolder;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.EntityManagerFactory;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.reactive.stage.Stage;
import org.hibernate.reactive.stage.impl.StageSessionImpl;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.data.querydsl.EntityPathResolver;
import org.springframework.data.querydsl.SimpleEntityPathResolver;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;
import org.springframework.data.util.ProxyUtils;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactiveJpaRepositoryFactoryBean<
        T extends Repository<S, ID>, S, ID extends Serializable>
    extends RepositoryFactoryBeanSupport<T, S, ID>
    implements ApplicationContextAware, BeanClassLoaderAware {

  private @Nullable ApplicationContext applicationContext;
  private StageReactiveJpaEntityOperations entityOperations;

  private EntityPathResolver entityPathResolver;

  private EscapeCharacter escapeCharacter = EscapeCharacter.DEFAULT;

  protected ReactiveJpaRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
    super(repositoryInterface);
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  @Override
  protected RepositoryFactorySupport createRepositoryFactory() {
    ReactiveJpaRepositoryFactory factory =
        new ReactiveJpaRepositoryFactory(
            entityOperations,
            entityOperations.sessionFactory(),
            applicationContext.getBean("entityManagerFactory", EntityManagerFactory.class));
    factory.setEscapeCharacter(escapeCharacter);
    // TODO
    factory.setQueryMethodFactory(
        new ReactiveJpaQueryMethodFactory(new DefaultReactiveJpaQueryExtractor()));
    factory.setQueryRewriterProvider(ReactiveQueryRewriterProvider.simple());

    //    RepositoryMetadata repositoryMetadata = factory.getRepositoryMetadata(getObjectType());
    //    factory.addRepositoryProxyPostProcessor(new ValueAdapterInterceptorProxyPostProcessor());
    //    factory.addRepositoryProxyPostProcessor(new SessionAwareProxyPostProcessor());
    factory.addRepositoryProxyPostProcessor(
        new SessionPostProcessor(entityOperations.sessionFactory()));

    return factory;
  }

  class ValueAdapterInterceptorProxyPostProcessor implements RepositoryProxyPostProcessor {

    //    private final RepositoryMetadata repositoryMetadata;

    //    public ValueAdapterInterceptorProxyPostProcessor(RepositoryMetadata repositoryMetadata) {
    //      this.repositoryMetadata = repositoryMetadata;
    //    }

    @Override
    public void postProcess(ProxyFactory factory, RepositoryInformation repositoryInformation) {
      //      factory.addAdvice(
      //          new ValueAdapterInterceptor(repositoryInformation,
      // getDefaultConversionService()));
    }

    class ValueAdapterInterceptor implements MethodInterceptor {

      private final RepositoryInformation repositoryInformation;
      private final GenericConversionService conversionService;

      public ValueAdapterInterceptor(
          RepositoryInformation repositoryInformation, GenericConversionService conversionService) {
        try {
          Field field =
              ReflectionUtils.findField(RepositoryFactorySupport.class, "CONVERSION_SERVICE");
          ReflectionUtils.makeAccessible(field);
          DefaultConversionService defaultConversionService =
              (DefaultConversionService) field.get(null);
          defaultConversionService.addConverter(
              new ConditionalGenericConverter() {
                @Override
                public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
                  return sourceType.getType() != targetType.getType();
                }

                @Override
                public Set<ConvertiblePair> getConvertibleTypes() {
                  return Set.of(new ConvertiblePair(Flux.class, List.class));
                }

                @Override
                public Object convert(
                    Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
                  return source;
                }
              });
          this.repositoryInformation = repositoryInformation;
          this.conversionService = conversionService;
        } catch (Exception e) {
          throw new RuntimeException(e.getMessage(), e);
        }
      }

      @Override
      public Object invoke(MethodInvocation invocation) throws Throwable {
        //        TypeInformation<?> returnType =
        // repositoryInformation.getReturnType(invocation.getMethod());
        //        Class<?> returnClass = returnType.getType();

        //        TypeInformation<?> componentType = returnType.getComponentType();
        //        Class<?> paramClass = componentType == null ? Object.class :
        // componentType.getType();

        Object proceeded = invocation.proceed();
        return adapt(proceeded, invocation);
      }

      private Object adapt(Object obj, MethodInvocation invocation) {
        TypeInformation<?> returnType = repositoryInformation.getReturnType(invocation.getMethod());
        Class<?> returnClass = returnType.getType();

        TypeInformation<?> componentType = returnType.getComponentType();
        Class<?> paramClass = componentType == null ? Object.class : componentType.getType();

        return conversionService.convert(obj, TypeDescriptor.forObject(obj));
        //        return converted.doOnError(e -> System.out.println("ngoai cung"));
      }
    }
  }

  class SessionAwareProxyPostProcessor implements RepositoryProxyPostProcessor {

    @Override
    public void postProcess(ProxyFactory factory, RepositoryInformation repositoryInformation) {
      //      factory.addAdvice(new SessionAwareInterceptor(repositoryInformation));
    }

    class SessionAwareInterceptor implements MethodInterceptor {

      protected final RepositoryInformation repositoryInformation;
      protected final TransactionAttributeSource tas;

      protected SessionAwareInterceptor(RepositoryInformation repositoryInformation) {
        this.repositoryInformation = repositoryInformation;
        this.tas =
            new CustomRepositoryAnnotationTransactionAttributeSource(repositoryInformation, true);
      }

      @Override
      @SuppressWarnings("unchecked")
      public Object invoke(MethodInvocation invocation) throws Throwable {
        //        Uni<Stage.Session> sessionUni = ReactiveJpaRepositoryFactoryBean.this
        //            .entityOperations.sessionFactory().openSession();
        //        TransactionAttributeSource tas = getTransactionAttributeSource();
        //        Class<?> targetClass =
        //            (invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis())
        // : null);
        //        final TransactionAttribute txAttr =
        //            (tas != null ? tas.getTransactionAttribute(invocation.getMethod(),
        // targetClass) : null);

        Object[] arguments = invocation.getArguments();
        Object[] newArgs = Arrays.copyOf(arguments, arguments.length + 2);

        /*if (noTransaction(txAttr)) {
          return toWrapper(
              ReactiveJpaRepositoryFactoryBean.this
                  .entityOperations
                  .sessionFactory()
                  .withSession(
                      session -> {
                        try {
                          prepareInvocation(
                              (ReflectiveMethodInvocation) invocation, newArgs, session, null);
                          Object proceeded = invocation.proceed();
                          if (proceeded instanceof Uni<?> uni) {
                            return uni;
                          }
                          return toWrapper(proceeded, Uni.class);
                        } catch (Throwable e) {
                          throw new RuntimeException(e.getMessage(), e);
                        }
                      }),
              invocation.getMethod().getReturnType());
        } else {*/
        //          AtomicReference<Throwable> throwableAtomicReference = new AtomicReference<>();
        //          AtomicBoolean flag = new AtomicBoolean(false);
        Uni<StageSessionImpl> uni = Uni.createFrom().nullItem();
        //            ReactiveJpaRepositoryFactoryBean.this
        //                .entityOperations
        //                .sessionFactory()
        //                .openSession()
        //                .map(StageSessionImpl.class::cast)
        /*.flatMap(s -> {
          try {
            prepareInvocation((ReflectiveMethodInvocation) invocation, newArgs, s, null);
            return Uni.createFrom().item(s);
          } catch (Exception e) {
            return Uni.createFrom().failure(e);
          }
        })*/ ;

        Mono<Object> filter =
            forCurrentTransaction()
                .mapNotNull(
                    tsm ->
                        tsm.getResource(
                            ReactiveJpaRepositoryFactoryBean.this.entityOperations
                                .sessionFactory()))
                .filter(ConnectionHolder.class::isInstance)
                .onErrorResume(e -> Mono.empty())
                .cache();

        Mono<Boolean> transactionExists =
            filter.map(Objects::nonNull).defaultIfEmpty(Boolean.FALSE);

        Mono<StageSessionImpl> session =
            filter
                .map(ConnectionHolder.class::cast)
                .map(ConnectionHolder::getConnection)
                .switchIfEmpty(
                    Mono.defer(() -> (Mono<StageSessionImpl>) toWrapper(uni, Mono.class)))
                .flatMap(
                    s -> {
                      try {
                        prepareInvocation(
                            (ReflectiveMethodInvocation) invocation, newArgs, s, null);
                        return Mono.just(s);
                      } catch (Exception e) {
                        return Mono.error(e);
                      }
                    })
                .cache();
        //          Mono<StageSessionImpl> sessionCached = session.cache();

        /*return Mono.usingWhen(sessionCached
         */
        /*.flatMap(s -> Mono.fromCompletionStage(s.getReactiveConnection().beginTransaction()).thenReturn(s))*/
        /*,
        s -> {
          //In case of SimpleReactiveRepository, proceeded is Uni type, then adapt to Mono
          //In case of custom UPDATE, custom COUNT (Transactional) or PartTree DELETE, proceeded is Mono<Long> or Mono<Integer>
          //or Uni<Long> or Uni<Integer> (as supporting multiple Reactive libs)
          //then adapt to Mono
          Object proceeded;
          try {
            proceeded = invocation.proceed();
          } catch (Throwable throwable) {
            proceeded = Mono.error(throwable);
          }

          //TODO: In case of SELECT Flux (Transactional), must adapt from Flux to Mono<List<?>>, re-check later
          if(proceeded instanceof Flux<?> f) {
            proceeded = f.collectList();
          } else {
            proceeded = toWrapper(proceeded, Mono.class);
          }

          return (Mono<?>) proceeded;
        },
        s -> Mono.fromCompletionStage(s.getReactiveConnection().commitTransaction()),
        (tuple2, throwable) -> Mono.empty(),
        s -> Mono.fromCompletionStage(s.getReactiveConnection().rollbackTransaction()))
        .onErrorResume(ex -> {
          if(txAttr.rollbackOn(ex)) {
            return sessionCached.flatMap(s -> Mono.fromCompletionStage(s.getReactiveConnection().rollbackTransaction()))
                .then(Mono.error(ex));
          } else {
            return sessionCached.flatMap(s -> Mono.fromCompletionStage(s.getReactiveConnection().commitTransaction()))
                .then(Mono.error(ex));
          }
        });*/

        return toWrapper(
            session
                .flatMap(
                    s -> {
                      // In case of SimpleReactiveRepository, proceeded is Uni type, then adapt to
                      // Mono
                      // In case of custom UPDATE, custom COUNT (Transactional) or PartTree DELETE,
                      // proceeded is Mono<Long> or Mono<Integer>
                      // or Uni<Long> or Uni<Integer> (as supporting multiple Reactive libs)
                      // then adapt to Mono
                      Object proceeded;
                      try {
                        proceeded = invocation.proceed();
                      } catch (Throwable throwable) {
                        proceeded = Mono.error(throwable);
                      }

                      // TODO: In case of SELECT Flux (Transactional), must adapt from Flux to
                      // Mono<List<?>>, re-check later
                      // Uni<List> will be converted to Mono<List>, then go to Flux.usingWhen -->
                      // Flux<List>
                      /*if(proceeded instanceof Flux<?> f) {
                        proceeded = f.collectList();
                      } else {*/
                      proceeded = toWrapper(proceeded, Mono.class);
                      /*}*/

                      return (Mono<?>) proceeded;
                    })
                .flatMap(
                    r ->
                        transactionExists.flatMap(
                            b -> {
                              if (b) {
                                return Mono.just(r);
                              }
                              return session
                                  .flatMap(
                                      s ->
                                          Mono.fromCompletionStage(
                                              s.getReactiveConnection().close()))
                                  .thenReturn(r);
                            }))
                .onErrorResume(
                    e ->
                        transactionExists.flatMap(
                            b -> {
                              if (b) {
                                return Mono.error((Throwable) e);
                              }
                              return session
                                  .flatMap(
                                      s ->
                                          Mono.fromCompletionStage(
                                              s.getReactiveConnection().close()))
                                  .then(Mono.error((Throwable) e));
                            })),
            invocation.getMethod().getReturnType());

        //          uni = uni.

        /*.withTransaction(
                    (session, transaction) -> {
                      try {
                        prepareInvocation(
                            (ReflectiveMethodInvocation) invocation,
                            newArgs,
                            session,
                            transaction);
                        Object proceeded = invocation.proceed();
                        Uni tmp;
                        if (proceeded instanceof Uni<?> u) {
                          tmp = u;
                        } else {
                          tmp = toWrapper(proceeded, Uni.class);
                        }

                        return tmp.onFailure(
                                throwable -> {
                                  boolean rollback = txAttr.rollbackOn((Throwable) throwable);
                                  throwableAtomicReference.set((Throwable) throwable);
                                  if (rollback) {
                                    transaction.markForRollback();
                                  } else {
                                    flag.set(Boolean.TRUE);
                                  }
                                  return !rollback;
                                })
                            .recoverWithNull();
                      } catch (Throwable e) {
                        if (txAttr.rollbackOn(e)) {
                          transaction.markForRollback();
                        }
                        return Uni.createFrom().failure(new RuntimeException(e.getMessage(), e));
                      }
                    });
        uni =
            uni.onItem()
                .ifNull()
                .switchTo(
                    () -> {
                      if (flag.get()) {
                        return Uni.createFrom().failure(throwableAtomicReference::get);
                      }
                      return Uni.createFrom().nullItem();
                    });
        return toWrapper(uni, invocation.getMethod().getReturnType());*/
        //        }

      }

      /*public TransactionAttributeSource getTransactionAttributeSource() {
        return this.tas;
      }*/

      private void prepareInvocation(
          ReflectiveMethodInvocation invocation,
          Object[] newArgs,
          Stage.Session session,
          Stage.Transaction transaction)
          throws Exception {
        newArgs[newArgs.length - 2] = session;
        newArgs[newArgs.length - 1] = transaction;
        ReflectiveMethodInvocation reflectiveMethodInvocation = invocation;
        reflectiveMethodInvocation.setArguments(newArgs);

        Method method = reflectiveMethodInvocation.getMethod();
        Object target = reflectiveMethodInvocation.getThis();
        Method newMethod = getMethod(method, target, newArgs);

        Field field = ReflectiveMethodInvocation.class.getDeclaredField("method");
        setFinal(reflectiveMethodInvocation, field, newMethod);
      }

      private boolean noTransaction(TransactionAttribute txAttr) {
        return txAttr == null;
      }

      static void setFinal(Object object, Field field, Object newValue) throws Exception {

        var lookup = MethodHandles.privateLookupIn(Field.class, MethodHandles.lookup());
        VarHandle modifiers = lookup.findVarHandle(Field.class, "modifiers", int.class);

        modifiers.set(field, field.getModifiers() & ~Modifier.FINAL);

        //        final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        //        unsafeField.setAccessible(true);
        //        final Unsafe unsafe = (Unsafe) unsafeField.get(null);
        //
        //
        //        Object staticFieldBase = unsafe.staticFieldBase(field);
        //        long staticFieldOffset = unsafe.staticFieldOffset(field);
        //        unsafe.putObject(staticFieldBase, staticFieldOffset, newValue);

        field.setAccessible(true);
        //
        //        Field modifiersField = Field.class.getDeclaredField("modifiers");
        //        modifiersField.setAccessible(true);
        //        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        //
        field.set(object, newValue);
      }

      protected Method getMethod(Method method, Object target, Object[] newArgs) throws Exception {
        String name = method.getName();
        Object leafTarget = findLeafTarget(target);
        Method[] allDeclaredMethods = ReflectionUtils.getDeclaredMethods(leafTarget.getClass());

        return Arrays.stream(allDeclaredMethods)
            .filter(
                candidate ->
                    candidate != method
                        && candidate.getName().equals(name)
                        && matchArgs(candidate, newArgs))
            .findFirst()
            .orElse(method);
      }

      private Object findLeafTarget(Object target) throws Exception {
        if (AopUtils.isJdkDynamicProxy(target)) {
          Object temp = ((Advised) target).getTargetSource().getTarget();
          return findLeafTarget(temp);
        }

        return target;
      }

      private boolean matchArgs(Method candidate, Object[] newArgs) {
        Class<?>[] parameterTypes = candidate.getParameterTypes();
        if (parameterTypes.length == 0 && (newArgs == null || newArgs.length == 0)) {
          return true;
        }

        if (parameterTypes.length != newArgs.length) {
          return false;
        }

        for (int i = 0; i < parameterTypes.length; i++) {
          Object arg = newArgs[i];
          if (arg == null) {
            continue;
          }

          Class<?> paramClass = parameterTypes[i];
          if (paramClass != arg.getClass() && !paramClass.isAssignableFrom(arg.getClass())) {
            return false;
          }
        }

        return true;
      }

      static class CustomRepositoryAnnotationTransactionAttributeSource
          extends AnnotationTransactionAttributeSource {

        protected final boolean enableDefaultTransactions;
        protected final RepositoryInformation repositoryInformation;

        public CustomRepositoryAnnotationTransactionAttributeSource(
            RepositoryInformation repositoryInformation, boolean enableDefaultTransactions) {

          super(true);

          Assert.notNull(repositoryInformation, "RepositoryInformation must not be null");

          this.enableDefaultTransactions = enableDefaultTransactions;
          this.repositoryInformation = repositoryInformation;
        }

        @Override
        @Nullable
        protected TransactionAttribute computeTransactionAttribute(
            Method method, @Nullable Class<?> targetClass) {

          // Don't allow no-public methods as required.
          if (allowPublicMethodsOnly() && !Modifier.isPublic(method.getModifiers())) {
            return null;
          }

          // Ignore CGLIB subclasses - introspect the actual user class.
          Class<?> userClass =
              targetClass == null ? targetClass : ProxyUtils.getUserClass(targetClass);

          // The method may be on an interface, but we need attributes from the target class.
          // If the target class is null, the method will be unchanged.
          Method specificMethod = ClassUtils.getMostSpecificMethod(method, userClass);

          // If we are dealing with method with generic parameters, find the original method.
          specificMethod = BridgeMethodResolver.findBridgedMethod(specificMethod);

          TransactionAttribute txAtt = null;

          if (specificMethod != method) {

            // Fallback is to look at the original method.
            txAtt = findTransactionAttribute(method);

            if (txAtt != null) {
              return txAtt;
            }

            // Last fallback is the class of the original method.
            txAtt = findTransactionAttribute(method.getDeclaringClass());

            if (txAtt != null || !enableDefaultTransactions) {
              return txAtt;
            }
          }

          // First try is the method in the target class.
          txAtt = findTransactionAttribute(specificMethod);

          if (txAtt != null) {
            return txAtt;
          }

          // Second try is the transaction attribute on the target class.
          txAtt = findTransactionAttribute(specificMethod.getDeclaringClass());

          if (txAtt != null) {
            return txAtt;
          }

          if (!enableDefaultTransactions) {
            return null;
          }

          // Fallback to implementation class transaction settings of nothing found
          // return findTransactionAttribute(method);
          Method targetClassMethod = repositoryInformation.getTargetClassMethod(method);

          if (targetClassMethod.equals(method)) {
            return null;
          }

          txAtt = findTransactionAttribute(targetClassMethod);

          if (txAtt != null) {
            return txAtt;
          }

          txAtt = findTransactionAttribute(targetClassMethod.getDeclaringClass());

          if (txAtt != null) {
            return txAtt;
          }

          return null;
        }
      }
    }
  }

  //  @Autowired
  public void setEntityOperations(@Nullable StageReactiveJpaEntityOperations entityOperations) {
    this.entityOperations = entityOperations;
  }

  @Autowired
  public void setEntityPathResolver(ObjectProvider<EntityPathResolver> resolver) {
    this.entityPathResolver = resolver.getIfAvailable(() -> SimpleEntityPathResolver.INSTANCE);
  }

  public void setEscapeCharacter(char escapeCharacter) {
    this.escapeCharacter = EscapeCharacter.of(escapeCharacter);
  }
}
