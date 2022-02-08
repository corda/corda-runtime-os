import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.db.connection.manager.createFromConfig
import net.corda.db.connection.manager.impl.DbConnectionsRepositoryImpl
import net.corda.db.core.DataSourceFactory
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigObject
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.orm.EntityManagerFactoryFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Connection
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.Query
import javax.sql.DataSource

class DbConnectionsRepositoryTest {
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
    private val clusterConnection = mock<Connection>()
    private val otherConnection = mock<Connection>()
    private val clusterDataSource = mock<DataSource>() {
        on { connection }.doReturn(clusterConnection)
    }
    private val otherDataSource = mock<DataSource>() {
        on { connection }.doReturn(otherConnection)
    }
    private val config = ConfigFactory.parseString("config=123")
    private val configObj = mock<SmartConfigObject>() {
        on { render(any<ConfigRenderOptions>()) }.doReturn("config=123")
    }
    private val otherDbConfig = mock<SmartConfig>() {
        on { withFallback(any()) }.doReturn(this.mock)
        on { hasPath(any()) }.doReturn(true)
        on { getString(any()) }.doReturn("other DB config")
        on { root() }.doReturn(configObj)
    }
    private val configFactory = mock<SmartConfigFactory>() {
        on { create(config) }.doReturn(otherDbConfig)
    }
    private val clusterDbConfig = mock<SmartConfig>() {
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
    fun `initialise sets cluster db connection`() {
        val repository = DbConnectionsRepositoryImpl(entityManagerFactoryFactory, dataSourceFactory, duration, sleeper)
        repository.initialise(clusterDbConfig)

        assertThat(repository.clusterDataSource).isSameAs(clusterDataSource)
    }

    @Test
    fun `initialise verifies the DB connection`() {
        val repository = DbConnectionsRepositoryImpl(entityManagerFactoryFactory, dataSourceFactory, duration, sleeper)
        repository.initialise(clusterDbConfig)
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
            DbConnectionsRepositoryImpl(entityManagerFactoryFactory, dataSourceFactory, duration,
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

        val repository = DbConnectionsRepositoryImpl(entityManagerFactoryFactory, dataSourceFactory, duration, sleeper)
        repository.initialise(clusterDbConfig)
        repository.get(connectionJPA.name, connectionJPA.privilege)

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

        val repository = DbConnectionsRepositoryImpl(entityManagerFactoryFactory, dataSourceFactory, duration, sleeper)
        repository.initialise(clusterDbConfig)
        repository.put(
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

        verify(entityManager).persist(argThat { it ->
            assertThat(it).isInstanceOf(DbConnectionConfig::class.java)
            val dbc = it as DbConnectionConfig
            assertSoftly {
                it.assertThat(dbc.name).isEqualTo("test-connection")
                it.assertThat(dbc.privilege).isEqualTo(DbPrivilege.DML)
                it.assertThat(dbc.config).isEqualTo("config=123")
                it.assertThat(dbc.description).isEqualTo("super connection")
                it.assertThat(dbc.updateActor).isEqualTo("me")
            }
            true
        })
    }
}