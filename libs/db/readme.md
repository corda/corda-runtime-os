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

### OSGi

When used in an OSGi context, the following bundles are required 
(see `test.bndrun` in the `osgi-integration-tests` module):

* `net.bytebuddy.byte-buddy`: needed by hibernate - byte buddy has replaced javassist as the default bytecode provider
* db driver used by Hikari. E.g. `org.hsqldb.hsqldb` for in-memory (test) use, postgres TBC.

### Postgres

Start container like so:

```bash
docker run --rm --name test-instance -e POSTGRES_PASSWORD=password -p 5432:5432 postgres
```

NOTES: 
* we cannot use the `testcontainers` library as it does not work with OSGi.
* Change the port forwarding if needed.

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

#### OSGi integration testing

The `osgi-integration-tests` module contains a test suite to test/validate/prove the DB modules in an OSGi context.
The most important validation is persisting/querying entities across different OSGi bundles.

The `confirm we can query cross-bundle` test uses a query that uses enties in both dogs and cats bundles. This is a 
completely artificial scenario, and not one that we would expect to see in production code, but the sole purpose
of this test is to validate that this is technically possible.

## Data definition (DDL) / Liquibase

TODO