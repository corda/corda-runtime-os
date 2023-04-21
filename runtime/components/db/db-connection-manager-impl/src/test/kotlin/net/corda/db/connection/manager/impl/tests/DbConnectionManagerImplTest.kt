package net.corda.db.connection.manager.impl.tests

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.connection.manager.createFromConfig
import net.corda.db.connection.manager.impl.BootstrapConfigProvided
import net.corda.db.connection.manager.impl.DbConnectionManagerImpl
import net.corda.db.connection.manager.impl.DbConnectionRepositoryFactory
import net.corda.db.connection.manager.impl.LateInitDbConnectionOps
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DataSourceFactory
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigObject
import net.corda.libs.configuration.datamodel.DbConnectionAudit
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.times
import org.mockito.kotlin.firstValue
import org.mockito.kotlin.secondValue
import java.sql.Connection
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.Query

class DbConnectionManagerImplTest {
    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) }.doReturn(lifecycleCoordinator)
    }
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry>()
    private val bootstrapConfig = mock<SmartConfig>()

    private val q = mock<Query>() {
        on { resultList }.doReturn(emptyList<DbConnectionConfig>())
    }
    private val entityManager = mock<EntityManager>() {
        on { createNamedQuery(any()) }.doReturn(q)
    }
    private val entityManagerFactory = mock<EntityManagerFactory>{
        on { createEntityManager() }.doReturn(entityManager)
    }
    private val entityManagerFactoryFactory = mock<EntityManagerFactoryFactory>() {
        on { create(any(), any(), any()) }.doReturn(entityManagerFactory)
    }
    private val entityRegistry = mock<JpaEntitiesRegistry>() {
        on { get(any()) }.doReturn(mock<JpaEntitiesSet>())
    }
    private val clusterConnection = mock<Connection>()
    private val otherConnection = mock<Connection>()
    private val clusterDataSource = mock<CloseableDataSource>() {
        on { connection }.doReturn(clusterConnection)
    }
    private val otherDataSource = mock<CloseableDataSource>() {
        on { connection }.doReturn(otherConnection)
    }
    private val config = ConfigFactory.parseString("config=123")
    private val configObj = mock<SmartConfigObject>() {
        on { render(any<ConfigRenderOptions>()) }.doReturn("config=123")
    }

    private val configRoot = mock<SmartConfigObject>() {
        on { render(org.mockito.kotlin.any()) }.doReturn("[config]")
    }
    private val otherDbConfig = mock<SmartConfig>() {
        on { toSafeConfig() }.doReturn(this.mock)
        on { root() }.doReturn(configRoot)
        on { withFallback(any()) }.doReturn(this.mock)
        on { hasPath(any()) }.doReturn(true)
        on { getString(any()) }.doReturn("other DB config")
        on { root() }.doReturn(configObj)
    }
    private val configFactory = mock<SmartConfigFactory>() {
        on { create(config) }.doReturn(otherDbConfig)
    }
    private val clusterDbConfig = mock<SmartConfig>() {
        on { toSafeConfig() }.doReturn(this.mock)
        on { root() }.doReturn(configRoot)
        on { withFallback(any()) }.doReturn(this.mock)
        on { hasPath(any()) }.doReturn(true)
        on { getString(any()) }.doReturn("cluster DB config")
        on { mock.factory }.doReturn(configFactory)
    }
    private val dataSourceFactory = mock<DataSourceFactory>() {
        on { createFromConfig(clusterDbConfig) }.doReturn(clusterDataSource)
        on { createFromConfig(otherDbConfig) }.doReturn(otherDataSource)
    }

    private val duration = Duration.ofNanos(1)
    private val sleeper = { _: Duration -> println("Don't sleep") }


    @Test
    fun `when bootstrap post event`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            jpaEntitiesRegistry)
        mgr.bootstrap(bootstrapConfig)
        verify(lifecycleCoordinator).postEvent(BootstrapConfigProvided(bootstrapConfig))
    }

    @Test
    fun `when start post start event()`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            jpaEntitiesRegistry)
        mgr.start()
        verify(lifecycleCoordinator).start()
    }

    @Test
    fun `when stop post stop event()`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            jpaEntitiesRegistry)
        mgr.stop()
        verify(lifecycleCoordinator).stop()
    }

    @Test
    fun `when close post close event()`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            jpaEntitiesRegistry)
        mgr.close()
        verify(lifecycleCoordinator).close()
    }

    @Test
    fun `when is running return from coordinator()`() {
        val mgr = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory,
            entityManagerFactoryFactory,
            jpaEntitiesRegistry)
        mgr.isRunning
        verify(lifecycleCoordinator).isRunning
    }

    @Test
    fun `initialise sets cluster db connection`() {
        val dbConnectionManager = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory, dataSourceFactory, entityManagerFactoryFactory, entityRegistry,
            DbConnectionRepositoryFactory(), LateInitDbConnectionOps(), duration, sleeper)
        dbConnectionManager.initialise(clusterDbConfig)

        assertThat(dbConnectionManager.getClusterDataSource()).isSameAs(clusterDataSource)
    }

    @Test
    fun `initialise verifies the DB connection`() {
        val dbConnectionManager = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory, dataSourceFactory, entityManagerFactoryFactory, entityRegistry,
            DbConnectionRepositoryFactory(), LateInitDbConnectionOps(), duration, sleeper)
        dbConnectionManager.initialise(clusterDbConfig)
        verify(clusterConnection).close()
    }

    @Test
    fun `initial DB verification retries`() {
        var failFirst = false
        var succeedNext = false
        // first fail
        whenever(clusterConnection.close()).then {
            if(!failFirst) {
                failFirst = true
                @Suppress("TooGenericExceptionThrown")
                throw Exception("connection failed")
            } else {
                succeedNext = true
            }
        }

        val repository =
            DbConnectionManagerImpl(
                lifecycleCoordinatorFactory, dataSourceFactory, entityManagerFactoryFactory, entityRegistry,
                DbConnectionRepositoryFactory(), LateInitDbConnectionOps(), duration,
                // reconfigure mock after sleeping
                sleeper = { d -> println("Sleeping for $d") })

        repository.initialise(clusterDbConfig)

        assertThat(failFirst).isTrue
        assertThat(succeedNext).isTrue
    }

    @Test
    fun `when get connection, retrieve`() {
        val connectionJPA = DbConnectionConfig(
            UUID.randomUUID(),
            "test-connection",
            DbPrivilege.DDL,
            Instant.now(),
            "me",
            "foo",
            config.root().render()
        )

        val query = mock<Query>()
        whenever(entityManager.createNamedQuery(any())).doReturn(query)
        whenever(query.resultList).doReturn(listOf(connectionJPA))

        val dbConnectionManager = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory, dataSourceFactory, entityManagerFactoryFactory, entityRegistry,
            DbConnectionRepositoryFactory(), LateInitDbConnectionOps(), duration, sleeper)
        dbConnectionManager.initialise(clusterDbConfig)
        dbConnectionManager.getDataSource(connectionJPA.name, connectionJPA.privilege)

        verify(dataSourceFactory).createFromConfig(otherDbConfig)

        val paramNameCaptor = ArgumentCaptor.forClass(String::class.java)
        val paramValueCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(query, atLeastOnce()).setParameter(paramNameCaptor.capture(), paramValueCaptor.capture())
        assertThat(paramNameCaptor.allValues).contains("name","privilege")
        assertThat(paramValueCaptor.allValues.map { it.toString() }).contains("test-connection","DDL")
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `when put persist new connection`(existing: Boolean) {
        val query = mock<Query>() {
            on { resultList }.doReturn(
                if(existing) {
                    val connectionJPA = DbConnectionConfig(
                        UUID.randomUUID(),
                        "test-connection",
                        DbPrivilege.DML,
                        Instant.now(),
                        "me",
                        "foo",
                        config.root().render()
                    )
                    listOf(connectionJPA)
                } else {
                    emptyList<Any>()
                })
        }
        whenever(entityManager.createNamedQuery(any())).doReturn(query)
        whenever(entityManager.transaction).doReturn(mock())

        val dbConnectionManager = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory, dataSourceFactory, entityManagerFactoryFactory, entityRegistry,
            DbConnectionRepositoryFactory(), LateInitDbConnectionOps(), duration, sleeper)
        dbConnectionManager.initialise(clusterDbConfig)
        dbConnectionManager.putConnection(
            "test-connection",
            DbPrivilege.DML,
            otherDbConfig,
            "super connection",
            "me")

        // verify we loaded existing first (for concurrency control)
        val paramNameCaptor = ArgumentCaptor.forClass(String::class.java)
        val paramValueCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(query, atLeastOnce()).setParameter(paramNameCaptor.capture(), paramValueCaptor.capture())
        assertThat(paramNameCaptor.allValues).contains("name","privilege")
        assertThat(paramValueCaptor.allValues.map { it.toString() }).contains("test-connection","DML")

        val entityCaptor = ArgumentCaptor.forClass(Object::class.java)

        verify(entityManager, times(2)).persist(entityCaptor.capture())
        assertThat(entityCaptor.firstValue).isInstanceOf(DbConnectionConfig::class.java)
        assertThat(entityCaptor.secondValue).isInstanceOf(DbConnectionAudit::class.java)
        val dbc = entityCaptor.firstValue as DbConnectionConfig
        SoftAssertions.assertSoftly {
                it.assertThat(dbc.name).isEqualTo("test-connection")
                it.assertThat(dbc.privilege).isEqualTo(DbPrivilege.DML)
                it.assertThat(dbc.config).isEqualTo("config=123")
                it.assertThat(dbc.description).isEqualTo("super connection")
                it.assertThat(dbc.updateActor).isEqualTo("me")
        }
        val dba = entityCaptor.secondValue as DbConnectionAudit
        SoftAssertions.assertSoftly {
            it.assertThat(dba.name).isEqualTo("test-connection")
            it.assertThat(dba.privilege).isEqualTo(DbPrivilege.DML)
            it.assertThat(dba.config).isEqualTo("config=123")
            it.assertThat(dba.description).isEqualTo("super connection")
            it.assertThat(dba.updateActor).isEqualTo("me")
        }
    }

    @Test
    fun `when putConnection put on DbConnectionOps`() {

        val dbConnectionOps = mock<DbConnectionOps>()
        val dbConnectionManager = DbConnectionManagerImpl(
            lifecycleCoordinatorFactory, dataSourceFactory, entityManagerFactoryFactory, entityRegistry,
            DbConnectionRepositoryFactory(), dbConnectionOps, duration, sleeper)
        dbConnectionManager.initialise(clusterDbConfig)

        val em = mock<EntityManager>()

        val config = mock<SmartConfig>()
        val name = "test config"
        val priv = DbPrivilege.DDL
        val description = "A really awesome database"
        val actor = "Spiderman"
        dbConnectionManager.putConnection(em, name, priv, config, description, actor)

        verify(dbConnectionOps).putConnection(em, name, priv, config, description, actor)
    }
}
