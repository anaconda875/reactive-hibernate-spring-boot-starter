## This module's aim is to bring Reactive Hibernate support to Spring Data.
### Some features:
1. Useful Crud and Paging/Sorting methods (just like spring-data-jpa)
2. Custom query methods (findBy\*And\*OrderBy*, @Query("FROM Abc")), native queries are also supported
3. Support `@Transactional` (readOnly, rollbackFor, timeout, noRollbackFor, ...)
4. Support `@Modifying`, `@Param`
5. Support `@Lock`, `@EntityGraph`
6. Support `@NamedQuery`, `@NamedEntityGraph`
7. Support SpEL
8. Support Pagination
9. Support Auditing
10. Auto-config
11. Of course, it is truly non-blocking and compatible with Webflux

### Some remaining things:
1. Isolation level, savepoint
2. Stored procedure
3. Code optimization

## Getting started:
1. Dependency:
```xml
<dependency>
    <groupId>io.github.anaconda875</groupId>
    <artifactId>reactive-hibernate-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>
```
Sometimes you might need to add (in case of dependencies conflict):
```xml
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-core</artifactId>
    <version>6.4.4.Final</version>
    <scope>compile</scope>
</dependency>
```
2. Useful Crud and Paging/Sorting methods: see [CrudRepository](src/main/java/com/htech/data/jpa/reactive/repository/ReactivePagingAndSortingRepository.java) and [CrudRepository](src/main/java/com/htech/data/jpa/reactive/repository/ReactivePagingAndSortingRepository.java)
3. Custom query methods (with `Pageable`, `@Lock`, `@EntityGraph`, `@Param`, `@Transactional`, `@Modifying`):
```java
  @Lock(LockModeType.PESSIMISTIC_READ)
  @EntityGraph(attributePaths = {"content"})
  Flux<Post> findByContentOrderByCreatedAtDesc(String content);
```
```java
  @Query("SELECT p FROM Post p WHERE p.content = ?1")
  Mono<Page<Post>> findByContentCustomPage(String content, Pageable pageable);
```
```java
    @Query(
        nativeQuery = true,
        value =
            "SELECT id, title, content, created_at, created_by, last_modified_at, last_modified_by " +
                "FROM posts WHERE id = ?1")
    Mono<Post> nativeQ(UUID id);
```
```java
  @Query(
      nativeQuery = true,
      value =
          "SELECT id, title, content, created_at, created_by, last_modified_at, last_modified_by "
              + "FROM posts WHERE content = :content")
  Flux<Post> nativeQ2(@Param("content") String content);
```
```java
  @Modifying
  @Query(nativeQuery = true, value = "DELETE from posts WHERE content = :content")
  @Transactional
  Mono<?> deleteNative2(@Param("content") String content);
```
```java
  @Modifying
  @Query("DELETE FROM Post p WHERE p.title = :title")
  Mono<?> deleteCustom(@Param("title") String title);
```
4. Support `@NamedQuery`, `@NamedEntityGraph`, Auditing:
```java
@NamedQueries(
    value = {
      @NamedQuery(
          name = "Post.testNamed",
          query = "SELECT p FROM Post p WHERE p.content = :content")
    })
@NamedEntityGraphs({
  @NamedEntityGraph(
      name = "Post.testNamed",
      attributeNodes = {@NamedAttributeNode("title")})
})
public class Post {

  @Id
  @GeneratedValue(generator = "uuid")
  @GenericGenerator(name = "uuid", strategy = "uuid2")
  UUID id;

  String title;
  String content;

  //<Auditing>
  @Column(name = "created_at")
  @CreatedDate
  LocalDateTime createdAt;

  @Column(name = "last_modified_at")
  @LastModifiedDate
  LocalDateTime lastModifiedAt;

  @Column(name = "created_by")
  @CreatedBy
  String createdBy;

  @Column(name = "last_modified_by")
  @LastModifiedBy
  String lastModifiedBy;
  //</Auditing>
}
```
```java
  //In repository
  @Lock(LockModeType.READ)
  @EntityGraph
  Mono<Page<Post>> testNamed(String content, Pageable pageable);
```
```java
//Config for Pagination, Auditing
@Configuration
@EnableReactiveJpaAuditing(auditorAwareRef = "reactiveAuditorAware")
public class Config {

  @Bean
  WebFluxConfigurer webFluxConfigurer() {
    return new WebFluxConfigurer() {
      @Override
      public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
        configurer.addCustomResolver(new ReactivePageableHandlerMethodArgumentResolver());
      }
    };
  }

  @Bean
  ReactiveAuditorAware<String> reactiveAuditorAware() {
    return () ->
        ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getPrincipal)
            .map(UserDetails.class::cast)
            .map(UserDetails::getUsername);
  }

  @Bean
  SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(authorize -> authorize.anyExchange().authenticated())
        .httpBasic(Customizer.withDefaults())
        .build();
  }

  @Bean
  ReactiveUserDetailsService userDetailsService() {
    var isabelle = User.withUsername("admin").password("admin").authorities("admin").build();

    var bjorn =
        User.withUsername("anonymous").password("anonymous").authorities("anonymous").build();

    return new MapReactiveUserDetailsService(isabelle, bjorn);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return NoOpPasswordEncoder.getInstance();
  }

  @Bean
  ReactiveEvaluationContextExtension securityExtension() {
    return new ReactiveEvaluationContextExtension() {

      @Override
      public String getExtensionId() {
        return "webflux-security";
      }

      @Override
      public Mono<? extends EvaluationContextExtension> getExtension() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(SecurityEvaluationContextExtension::new);
      }
    };
  }
}

```
5. SpEL:
```java
  @Query(
      nativeQuery = true,
      value =
          "SELECT id, title, content, created_at, created_by, last_modified_at, last_modified_by FROM posts " +
              "WHERE created_by = :#{authentication.name} AND title = :title " +
              "AND last_modified_by = :#{authentication.name}")
  Mono<Post> testSpelNative2(@Param("title") String title);
```
```java
  @Query(
      "SELECT p from #{#entityName} p WHERE p.lastModifiedBy = :#{authentication.name} AND p.title = ?1 AND p.createdBy = :#{authentication.name}")
  @EntityGraph(attributePaths = {"createdBy"})
  Mono<Post> testSpel3(String title);
```

6. TO BE CONTINUED...

This is an example of how to use it (with Postgres): https://github.com/anaconda875/spring-hibernate-reactive-mutiny-example

If you guys find it useful for our business, feel free to use and report bugs to me