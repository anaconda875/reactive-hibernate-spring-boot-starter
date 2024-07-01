## This module's aim is to bring Reactive Hibernate support to Spring Data.
### Some features:
1. Useful Crud and Paging/Sorting methods (just like spring-data-jpa)
2. Custom query methods (findBy\*And\*OrderBy*, @Query("FROM Abc")), native queries are also supported
3. Support @Transactional (readOnly, rollbackFor, timeout, noRollbackFor, ...)
4. Support @Modifying, @Param
5. Support @Lock, @EntityGraph
6. Support spEL
7. Support Pagination
8. Support NamedQuery, NamedEntityGraph
9. Auto-config
10. Of course, it is truly non-blocking and compatible with Webflux

### Some remaining things:
1. Isolation level, savepoint
2. Code optimization

This is an example of how to use it (with Postgres): https://github.com/anaconda875/spring-hibernate-reactive-mutiny-example

If you guys find it useful for our business, feel free to use and report bugs to me