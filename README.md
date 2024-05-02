## This module's aim is to bring Reactive Hibernate support to Spring Data.
### Some features:
1. Useful Crud and Paging/Sorting methods (just like spring-data-jpa)
2. Custom query methods (findBy*And*OrderBy*, @Query("FROM Abc")), native queries are also supported
3. Support @Transactional, @Modifying, @Param
4. Auto-config
5. Of course, it is compatible with Webflux

### Some remaining things:
1. EntityGraph
2. LockMode
3. Code optimization

This is an example of how to use it (with Postgres): https://github.com/anaconda875/spring-hibernate-reactive-mutiny-example

Note: For Jdk 17 or up, add this VM option:  ```--add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED```

If you guys find it useful for our business, feel free to use and report bugs to me