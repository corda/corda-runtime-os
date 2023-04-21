# DB

This contains library modules to be used for DB interaction.

## Data Manipulation (DML)/ ORM

### ORM

`orm` and `orm-impl` are ORM abstractions. `orm` is the "internal api" module and does not expose references 
to implementation specific libraries such as Hibernate.
The main interface in this module is `EntityManagerFactoryFactory`, which exposes a way to create a concrete 
instance of `EntityManagerFactory` to manage given list of JPA annotated entities:

```kotlin
fun create(
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

`EntityManagerFactoryFactory` is an OSGi service, hence can be injected (see osgi integration test)

When used in an OSGi context, the following bundles are required 
(see `test.bndrun` in the `osgi-integration-tests` module):

* `net.bytebuddy.byte-buddy`: needed by hibernate - byte buddy has replaced javassist as the default bytecode provider
* db driver used by Hikari. E.g. `org.hsqldb.hsqldb` for in-memory (test) use.

### Postgres

Start container like so:

```bash
docker run --rm --name test-instance -e POSTGRES_PASSWORD=password -p 5432:5432 postgres
```

Set the `postgresPort` gradle property, e.g.:

```bash
gradle clean :libs:db:osgi-integration-tests:integrationTest -PpostgresPort=5432
```

NOTES: 
* we cannot use the `testcontainers` library as it does not work with OSGi.
* Change the port forwarding if needed.
* Other postgres properties could be added (e.g. host etc)
* System property must be set in the bndrun file, which can use gradle properties: `postgresPort=${project.postgresPort}`
* If integration test has to be executed repeatedly `clean` or `cleanTestOSGi` target has to be executed before `integrationTest`
or else Gradle may skip execution.

### Testing

#### Integration testing
`InMemoryEntityManagerConfiguration` is an implementation of `EntityManagerConfiguration` that supports `hsqldb`
"in memory" database.
This can be useful for testing.
The `hsqldb` driver needs to be a runtime dependency, for example:

```groovy
integrationTestRuntimeOnly "org.hsqldb:hsqldb:$hsqldbVersion"
```

`EntityManagerFactoryFactoryIntegrationTest` shows an example of an integration test using the in memory DB.

#### OSGi integration testing

The `osgi-integration-tests` module contains a test suite to test/validate/prove the DB modules in an OSGi context.
The most important validation is persisting/querying entities across different OSGi bundles.

The `confirm we can query cross-bundle` test uses a query that uses entities in both dogs and cats bundles. This is a 
completely artificial scenario, and not one that we would expect to see in production code, but the sole purpose
of this test is to validate that this is technically possible.
This test will also run DB migration scripts located in both bundles.

**Debugging OSGi Tests** 

OSGi integration tests run in a different process, which means you cannot debug them as you would normally do.
In order to debug them you need to specify a debug port in a system property (`runjdb`) and attach a remote
debugger.
For example, these integrations tests can be run like this:

```bash
gradle :libs:db:osgi-integration-tests:integrationTest -D-runjdb=1046
```

or against Postgres like so:

```bash
gradle :libs:db:osgi-integration-tests:integrationTest -D-runjdb=1046 -PpostgresPort=5432
```

## Data definition (DDL)

### Liquibase

We use [Liquibase](https://www.liquibase.org/) for DB schema management. For this, Schema definition needs to be 
provided in a DB Change Log that contains one or more Change Sets.
Normally these files are part of a JAR as resource files, or present on a file system, and Liquibase provide 
"out-the-box" `ResourceAccessor` implementations to support this.
However, we envisage we may have different ways of fetching these files. For example, DB change log files could
be fetched from a DB or Kafka where they were published as part of a CPI installation (TBC).
For this reason, we have an abstraction in `DbChange` with currently one implementation (`ClassloaderChangeLog`).
Internally this uses a `StreamResourceAcessor` which relies on the `DbChange` implementation being able to return
a Change Log file as a Stream.

### Liquibase and OSGi

The latest version of Liquibase (4.4.x) does not work in OSGi. This is related to Liquibase using the 
ServiceLoader to load an implementation of its own logging abstraction.
This [GitHub issue](https://github.com/liquibase/liquibase/issues/2054) describes the problem we were also seeing.
The patch in the associated PR does work.
Because API changed between 3.x and 4.x is impacting us, we have chosen to take a copy of the patch for
now and raised [CORE-2723](https://r3-cev.atlassian.net/browse/CORE-2723) to resolve this when the patch is released.

### Multiple Change Log files

Previous version of Corda have the ability to provide multiple "master" Change Log files. This is to support the
way we map from MappedSchema to Change Log files. 
In order to continue to support this, the `DbChange` interface also specifies "one or more" master change log files
must be specified.

### Multiple classloaders

Because of the above requirement, it is possible that ChangeLog files will exist in multiple bundles (or CPKs).
Therefore, we must support fetching from multiple classloaders.
This is achieved by being able to specify a classloader as well as master file(s) when creating a 
`ClassloaderChangeLog`.

This, however, brings an additional complication in that multiple classloaders could have identically named ChangeLog
files. For example, both cat and dog bundles may have a packages of `migration` that includes a migration file 
called `owner-v1.0.xml`. Because of the way Liquibase works, there is no way to know which script belongs to which 
bundle.

For this reason, we require a "name" attribute to be associated to each set of master ChangeLogs and classloader 
pairs, and we allow that full name to be used in file inclusion tags. E.g.:

```xml
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd">
    <include file="migration/cats-migration-v1.0.xml"/>
    <include file="classloader://net.cordapp.testing.bundles.cats/migration/owner-migration-v1.0.xml"/>
</databaseChangeLog>
```

This may not be necessary for CorDapp related migrations as we should be able to extract the migration files
from the CPKs at CPB/CPI installation time (and for example store them in the cluster DB), in which case we
could use CPK identifiers to uniquely define migration scripts belong to which bundles (TBC).

### DBMS specific changes

DBMS specific changes can be defined in the Change Log files by using the `dbms` attribute. E.g.:
```xml
<changeSet author="R3.Corda" id="test-migrations-v1.0" dbms="postgresql">
    <createTable tableName="postgres_table">
        <column name="id" type="INT">
            <constraints nullable="false"/>
        </column>
        <column name="name" type="VARCHAR(255)"/>
    </createTable>
</changeSet>
```
See an example of this in the Integration Tests. A full list of supported DBMS are 
[here](https://www.liquibase.org/get-started/databases?_ga=2.89667163.1554106465.1631635367-1762864281.1630587927)