package net.corda.db.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.util.DriverDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.sql.Connection
import java.time.Duration

class DataSourceFactoryImplTest {
    private val datasource = mock<CloseableDataSource>()
    private var hikariConfig: HikariConfig? = null
    private val connection = mock<Connection>()
    private val driverDataSource = mock<DriverDataSource>() {
        on { connection } doReturn connection
    }
    private val driverDatasourceMock =
        mock<(jdbcUrl: String,
              driverClass: String,
              username: String,
              password: String) -> DriverDataSource>() {
            on { invoke(any(), any(), any(), any()) } doReturn driverDataSource
        }
    private val factory = DataSourceFactoryImpl( {
        hikariConfig = it
        datasource
    }, driverDatasourceMock)


    @Test
    fun `create will set minimumIdle to maximumPoolSize if minimumPoolSize is set to null`() {
        factory.create(
            enablePool = true,
            driverClass = DataSourceFactoryImplTest::class.java.name,
            jdbcUrl = "url",
            username = "user",
            password = "password",
            maximumPoolSize = 44,
            minimumPoolSize = null,
            idleTimeout = Duration.ofMinutes(2),
            maxLifetime = Duration.ofMinutes(30),
            keepaliveTime = Duration.ZERO,
            validationTimeout = Duration.ofSeconds(5),
        )

        assertThat(hikariConfig?.minimumIdle).isEqualTo(44)
    }

    @Test
    fun `create will set the minimumIdle if minimumPoolSize is set`() {
        factory.create(
            enablePool = true,
            driverClass = DataSourceFactoryImplTest::class.java.name,
            jdbcUrl = "url",
            username = "user",
            password = "password",
            minimumPoolSize = 3,
            maximumPoolSize = 10,
            idleTimeout = Duration.ofMinutes(2),
            maxLifetime = Duration.ofMinutes(30),
            keepaliveTime = Duration.ZERO,
            validationTimeout = Duration.ofSeconds(5),
        )

        assertThat(hikariConfig?.minimumIdle).isEqualTo(3)
    }

    @Test
    fun `create will not set the idleTimeout if minimumPoolSize is not set`() {
        factory.create(
            enablePool = true,
            driverClass = DataSourceFactoryImplTest::class.java.name,
            jdbcUrl = "url",
            username = "user",
            password = "password",
            maximumPoolSize = 21,
            idleTimeout = Duration.ofDays(2),
            minimumPoolSize = null,
            maxLifetime = Duration.ofMinutes(30),
            keepaliveTime = Duration.ZERO,
            validationTimeout = Duration.ofSeconds(5),
        )

        assertThat(hikariConfig?.idleTimeout).isEqualTo(0)
    }

    @Test
    fun `create will not set the idleTimeout if minimumPoolSize is set to be the same as maximumPoolSize`() {
        factory.create(
            enablePool = true,
            driverClass = DataSourceFactoryImplTest::class.java.name,
            jdbcUrl = "url",
            username = "user",
            password = "password",
            maximumPoolSize = 21,
            minimumPoolSize = 21,
            idleTimeout = Duration.ofDays(2),
            maxLifetime = Duration.ofMinutes(30),
            keepaliveTime = Duration.ZERO,
            validationTimeout = Duration.ofSeconds(5),
        )

        assertThat(hikariConfig?.idleTimeout).isEqualTo(0)
    }

    @Test
    fun `create will set the idleTimeout if minimumPoolSize is not the same as maximumPoolSize`() {
        factory.create(
            enablePool = true,
            driverClass = DataSourceFactoryImplTest::class.java.name,
            jdbcUrl = "url",
            username = "user",
            password = "password",
            maximumPoolSize = 21,
            minimumPoolSize = 1,
            idleTimeout = Duration.ofDays(2),
            maxLifetime = Duration.ofMinutes(30),
            keepaliveTime = Duration.ZERO,
            validationTimeout = Duration.ofSeconds(5),
        )

        assertThat(hikariConfig?.idleTimeout).isEqualTo(Duration.ofDays(2).toMillis())
    }

    @Test
    fun `create without pool does not call hikarifactory`() {
        factory.create(
            enablePool = false,
            driverClass = DataSourceFactoryImplTest::class.java.name,
            jdbcUrl = "url",
            username = "user",
            password = "password",
            maximumPoolSize = 21,
            minimumPoolSize = 1,
            idleTimeout = Duration.ofDays(2),
            maxLifetime = Duration.ofMinutes(30),
            keepaliveTime = Duration.ZERO,
            validationTimeout = Duration.ofSeconds(5),
        )

        assertThat(hikariConfig).isNull()
    }

    @Test
    fun `create without pool creates DriverDataSource`() {
        factory.create(
            enablePool = false,
            driverClass = DataSourceFactoryImplTest::class.java.name,
            jdbcUrl = "url",
            username = "user",
            password = "password",
            maximumPoolSize = 21,
            minimumPoolSize = 1,
            idleTimeout = Duration.ofDays(2),
            maxLifetime = Duration.ofMinutes(30),
            keepaliveTime = Duration.ZERO,
            validationTimeout = Duration.ofSeconds(5),
        )

        verify(driverDatasourceMock)
            .invoke(
                "url",
                DataSourceFactoryImplTest::class.java.name,
                "user",
                "password"
            )
    }

    @Test
    fun `when getConnection set autoCommit and readOnly`() {
        val ds = factory.create(
            enablePool = false,
            driverClass = DataSourceFactoryImplTest::class.java.name,
            jdbcUrl = "url",
            username = "user",
            password = "password",
            maximumPoolSize = 21,
            minimumPoolSize = 1,
            idleTimeout = Duration.ofDays(2),
            maxLifetime = Duration.ofMinutes(30),
            keepaliveTime = Duration.ZERO,
            validationTimeout = Duration.ofSeconds(5),
            isAutoCommit = true,
            isReadOnly = true,
        )
        ds.connection

        verify(connection).isReadOnly = true
        verify(connection).autoCommit = true
    }
}