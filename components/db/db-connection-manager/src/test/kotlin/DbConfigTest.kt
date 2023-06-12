import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.createDbConfig
import net.corda.db.connection.manager.createFromConfig
import net.corda.db.core.DataSourceFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.secret.SecretsLookupService
import net.corda.schema.configuration.DatabaseConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Duration

class DbConfigTest {
    companion object {
        private const val DEFAULT_JDBC_URL = "jdbc:postgresql://cluster-db:5432/cordacluster"
        private const val JDBC_DRIVER = "org.postgresql.Driver"
        private const val DB_POOL_MAX_SIZE = 10
    }
    private val dataSourceFactory =  mock<DataSourceFactory>()
    private val secretConfig = SmartConfigImpl(ConfigFactory.parseMap(mapOf(
        "secret" to "secret value"
    )), mock(), mock())
    private val smartConfigFactory = mock<SmartConfigFactory>() {
        on { makeSecret(any(), any()) }.doReturn(secretConfig)
    }
    private val secretsLookupService = mock<SecretsLookupService>()

    private val user = "user"
    private val pass = "pass"
    private val driver = "driver"
    private val url = "url"
    private val poolsize = 987
    private val minPoolSize = 5
    private val idleTimeout = 120
    private val maxLifetime = 1800
    private val keepaliveTime = 0
    private val validationTimeout = 5

    private val fullConfig = """
        ${DatabaseConfig.JDBC_DRIVER}=$driver
        ${DatabaseConfig.JDBC_URL}=$url
        ${DatabaseConfig.DB_POOL_MAX_SIZE}=$poolsize
        ${DatabaseConfig.DB_USER}=$user
        ${DatabaseConfig.DB_PASS}=$pass
        ${DatabaseConfig.DB_POOL_MIN_SIZE}=null
        ${DatabaseConfig.DB_POOL_IDLE_TIMEOUT}=$idleTimeout
        ${DatabaseConfig.DB_POOL_MAX_LIFETIME}=$maxLifetime
        ${DatabaseConfig.DB_POOL_KEEP_ALIVE_TIME}=$keepaliveTime
        ${DatabaseConfig.DB_POOL_VALIDATION_TIMEOUT}=$validationTimeout
    """.trimIndent()
    private val fullSmartConfig = SmartConfigImpl(
        ConfigFactory.parseString(fullConfig),
        smartConfigFactory,
        secretsLookupService
    )

    private val minimalConfig = """
        ${DatabaseConfig.DB_USER}=user
        ${DatabaseConfig.DB_PASS}=pass
    """.trimIndent()
    private val minimalSmartConfig = SmartConfigImpl(
        ConfigFactory.parseString(minimalConfig),
        mock(),
        mock()
    )

    @Test
    fun `when valid config return datasource`() {
        dataSourceFactory.createFromConfig(fullSmartConfig)

        verify(dataSourceFactory).create(
            driverClass = driver,
            jdbcUrl = url,
            username = user,
            password = pass,
            maximumPoolSize = poolsize,
            minimumPoolSize = null,
            idleTimeout = durationOfSeconds(idleTimeout),
            maxLifetime = durationOfSeconds(maxLifetime),
            keepaliveTime = durationOfSeconds(keepaliveTime),
            validationTimeout = durationOfSeconds(validationTimeout)
        )
    }

    private fun durationOfSeconds(duration: Int) =
        Duration.ofSeconds(duration.toLong())

    @Test
    fun `when default config return datasource`() {
        dataSourceFactory.createFromConfig(minimalSmartConfig)

        verify(dataSourceFactory).create(
            JDBC_DRIVER,
            DEFAULT_JDBC_URL,
            user,
            pass,
            maximumPoolSize = DB_POOL_MAX_SIZE,
            minimumPoolSize = null,
            idleTimeout = durationOfSeconds(idleTimeout),
            maxLifetime = durationOfSeconds(maxLifetime),
            keepaliveTime = durationOfSeconds(keepaliveTime),
            validationTimeout = durationOfSeconds(validationTimeout)
        )
    }

    @Test
    fun `when minimum pull size exists, the createFromConfig will read it`() {
        val configWithMin = minimalSmartConfig.withValue(
            DatabaseConfig.DB_POOL_MIN_SIZE,
            ConfigValueFactory.fromAnyRef(minPoolSize),
        )

        dataSourceFactory.createFromConfig(configWithMin)

        verify(dataSourceFactory).create(
            JDBC_DRIVER,
            DEFAULT_JDBC_URL,
            user,
            pass,
            maximumPoolSize = DB_POOL_MAX_SIZE,
            minimumPoolSize = minPoolSize,
            idleTimeout = durationOfSeconds(idleTimeout),
            maxLifetime = durationOfSeconds(maxLifetime),
            keepaliveTime = durationOfSeconds(keepaliveTime),
            validationTimeout = durationOfSeconds(validationTimeout)
        )
    }

    @Test
    fun `when username missing throw`() {
        assertThrows<DBConfigurationException> {
            dataSourceFactory.createFromConfig(SmartConfigImpl(
                ConfigFactory.parseString("${DatabaseConfig.DB_PASS}=pass"),
                mock(),
                mock()))
        }
    }

    @Test
    fun `when pass missing throw`() {
        assertThrows<DBConfigurationException> {
            dataSourceFactory.createFromConfig(SmartConfigImpl(
                ConfigFactory.parseString("${DatabaseConfig.DB_PASS}=user"),
                mock(),
                mock()))
        }
    }

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