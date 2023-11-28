package net.corda.db.core

import com.zaxxer.hikari.HikariConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Duration

class HikariDataSourceFactoryTest {
    private val datasource = mock<CloseableDataSource>()
    private var hikariConfig: HikariConfig? = null
    private val factory = HikariDataSourceFactory {
        hikariConfig = it
        datasource
    }

    @Test
    fun `create will set minimumIdle to maximumPoolSize if minimumPoolSize is set to null`() {
        factory.create(
            driverClass = HikariDataSourceFactoryTest::class.java.name,
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
            driverClass = HikariDataSourceFactoryTest::class.java.name,
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
            driverClass = HikariDataSourceFactoryTest::class.java.name,
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
            driverClass = HikariDataSourceFactoryTest::class.java.name,
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
            driverClass = HikariDataSourceFactoryTest::class.java.name,
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
}
