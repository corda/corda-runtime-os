import net.corda.db.connection.manager.impl.DbConnectionsRepositoryImpl
import net.corda.db.connection.manager.impl.createFromConfig
import net.corda.db.core.DataSourceFactory
import net.corda.libs.configuration.SmartConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Connection
import javax.sql.DataSource

class DbConnectionsRepositoryTest {
    private val connection = mock<Connection>()
    private val dataSource = mock<DataSource>() {
        on { connection }.doReturn(connection)
    }
    private val config = mock<SmartConfig>() {
        on { withFallback(any()) }.doReturn(this.mock)
        on { hasPath(any()) }.doReturn(true)
        on { getString(any()) }.doReturn("config")
    }
    private val dataSourceFactory = mock<DataSourceFactory>() {
        on { createFromConfig(config) }.doReturn(dataSource)
    }

    @Test
    fun `initialise sets cluster db connection`() {
        val repository = DbConnectionsRepositoryImpl(dataSourceFactory)
        repository.initialise(config)

        assertThat(repository.clusterDataSource).isSameAs(dataSource)
    }

    @Test
    fun `initialise verifies the DB connection`() {
        val repository = DbConnectionsRepositoryImpl(dataSourceFactory)
        repository.initialise(config)
        verify(connection).close()
    }

    @Test
    fun `initial DB verification retries`() {
        var failFirst = false
        var succeedNext = false
        // first fail
        whenever(connection.close()).then {
            if(!failFirst) {
                failFirst = true
                @Suppress("TooGenericExceptionThrown")
                throw Exception("connection failed")
            } else {
                succeedNext = true
            }
        }

        val repository =
            DbConnectionsRepositoryImpl(dataSourceFactory,
                // reconfigure mock after sleeping
                sleeper = { d -> println("Sleeping for $d") })

        repository.initialise(config)

        assertThat(failFirst).isTrue
        assertThat(succeedNext).isTrue
    }
}