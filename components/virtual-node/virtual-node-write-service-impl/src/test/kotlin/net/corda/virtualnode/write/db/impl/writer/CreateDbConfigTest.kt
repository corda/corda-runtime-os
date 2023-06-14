package net.corda.virtualnode.write.db.impl.writer

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.schema.configuration.DatabaseConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class CreateDbConfigTest {

    private val secretConfig = SmartConfigImpl(
        ConfigFactory.parseMap(mapOf(
        "secret" to "secret value"
    )), mock(), mock())
    private val smartConfigFactory = mock<SmartConfigFactory>() {
        on { makeSecret(any(), any()) }.doReturn(secretConfig)
    }

    private val user = "user"
    private val pass = "pass"
    private val driver = "driver"
    private val url = "url"
    private val poolsize = 987
    private val minPoolSize = 5

    @Test
    fun `when createDbConfig can be read`() {
        val createdConfig = createDbConfig(
            smartConfigFactory,
            user,
            pass,
            driver,
            url,
            poolsize,
            minPoolSize,
            idleTimeout = 0,
            maxLifetime = 0,
            keepaliveTime = 0,
            validationTimeout = 0,
            key = "database-password")

        assertThat(createdConfig.getString(DatabaseConfig.DB_USER)).isEqualTo(user)
        assertThat(createdConfig.getConfig(DatabaseConfig.DB_PASS)).isEqualTo(secretConfig)
        assertThat(createdConfig.getString(DatabaseConfig.DB_PASS + ".secret")).isEqualTo("secret value")
        assertThat(createdConfig.getString(DatabaseConfig.JDBC_DRIVER)).isEqualTo(driver)
        assertThat(createdConfig.getString(DatabaseConfig.JDBC_URL)).isEqualTo(url)
        assertThat(createdConfig.getInt(DatabaseConfig.DB_POOL_MAX_SIZE)).isEqualTo(poolsize)
        assertThat(createdConfig.getInt(DatabaseConfig.DB_POOL_MIN_SIZE)).isEqualTo(minPoolSize)
    }

    @Test
    fun `when createDbConfig leave default driver empty`() {
        val createdConfig = createDbConfig(
            smartConfigFactory,
            user,
            pass,
            jdbcUrl = url,
            maxPoolSize = poolsize,
            minPoolSize = null,
            idleTimeout = 0,
            maxLifetime = 0,
            keepaliveTime = 0,
            validationTimeout = 0,
            key = "database-password"
        )

        assertThat(createdConfig.hasPath(DatabaseConfig.JDBC_DRIVER)).isFalse
    }

    @Test
    fun `when createDbConfig leave default min pool size empty`() {
        val createdConfig = createDbConfig(
            smartConfigFactory,
            user,
            pass,
            jdbcUrl = url,
            maxPoolSize = poolsize,
            minPoolSize = null,
            jdbcDriver = driver,
            idleTimeout = 0,
            maxLifetime = 0,
            keepaliveTime = 0,
            validationTimeout = 0,
            key = "database-password")

        assertThat(createdConfig.hasPath(DatabaseConfig.DB_POOL_MIN_SIZE)).isFalse
    }
}