# DB

This contains library modules to be used for DB interaction.

## Data Manipulation (DML)/ ORM

### ORM

`orm` and `orm-impl` are ORM abstractions. `orm` is the "internal api" module and does not expose references 
to implementation specific libraries such as Hibernate.
The main interface in this module is `EntityManagerFactoryFactory`, which exposes a way to create a concrete 
instance of `EntityManagerFactory` to manage given list of JPA annotated entities:

```kotlin
fun createEntityManagerFactory(
        persistenceUnitName: String,
        entities: List<Class<*>>,
        configuration: EntityManagerConfiguration
    ): EntityManagerFactory
```

* `persistenceUnitName` is a logical name for the `EntityManagerFactory`'s scope and is, for example, 
used for logging.
* `entities` is the list of JPA annotated entities that are being managed by the `EntityManager`. These entities 
can be from different OSGi bundles as the classloader associated to the `Class` will be used.
* `configuration` defines the data source configuration.

`EntityManagerFactory` should be considered expensive to create, so can be cached. `EntityManager`s on the other
hand should be considered lightweight.

`orm-impl` contains a hibernate JPA, OSGi aware, implementation of `EntityManagerFactoryFactory`. It supports
managing entities defined in separate OSGi bundles (and hence CPKs). The implementation uses a custom 
implementation of `PersistenceUnitInfo` so to avoid the need of any XML configuration files.

### Postgres

TODO

### Testing

#### Integration testing
`InMemoryEntityManagerConfiguration` is an implementation of `EntityManagerConfiguration` that supports `hsqldb`
"in memory" database.
This can be useful for testing.
The `hsqldb` driver needs to be a runtime dependency, for example:

```groovy
integrationTestRuntime "org.hsqldb:hsqldb:$hsqldbVersion"
```

`EntityManagerFactoryFactoryIntegrationTest` shows an example of an integration test using the in memory DB.

#### end-to-end testing

TODO: end to end test using Postgres and entities in separate OSGi bundles.

## Data definition (DDL) / Liquibase

TODO