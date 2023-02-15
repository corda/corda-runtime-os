import com.typesafe.config.ConfigFactory
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
    private val fullConfig = """
        ${DatabaseConfig.JDBC_DRIVER}=$driver
        ${DatabaseConfig.JDBC_URL}=$url
        ${DatabaseConfig.DB_POOL_MAX_SIZE}=$poolsize
        ${DatabaseConfig.DB_USER}=$user
        ${DatabaseConfig.DB_PASS}=$pass
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
            driver,
            url,
            user,
            pass,
            false,
            poolsize)
    }

    @Test
    fun `when default config return datasource`() {
        dataSourceFactory.createFromConfig(minimalSmartConfig)

        verify(dataSourceFactory).create(
            JDBC_DRIVER,
            DEFAULT_JDBC_URL,
            user,
            pass,
            false,
            DB_POOL_MAX_SIZE)
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
        val createdConfig = createDbConfig(smartConfigFactory, user, pass, driver, url, poolsize)

        assertThat(createdConfig.getString(DatabaseConfig.DB_USER)).isEqualTo(user)
        assertThat(createdConfig.getConfig(DatabaseConfig.DB_PASS)).isEqualTo(secretConfig)
        assertThat(createdConfig.getString(DatabaseConfig.DB_PASS + ".secret")).isEqualTo("secret value")
        assertThat(createdConfig.getString(DatabaseConfig.JDBC_DRIVER)).isEqualTo(driver)
        assertThat(createdConfig.getString(DatabaseConfig.JDBC_URL)).isEqualTo(url)
        assertThat(createdConfig.getInt(DatabaseConfig.DB_POOL_MAX_SIZE)).isEqualTo(poolsize)
    }

    @Test
    fun `when createDbConfig leave default driver empty`() {
        val createdConfig = createDbConfig(smartConfigFactory, user, pass, jdbcUrl = url, maxPoolSize = poolsize)

        assertThat(createdConfig.hasPath(DatabaseConfig.JDBC_DRIVER)).isFalse
    }

    @Test
    fun `when createDbConfig leave default url empty`() {
        val createdConfig = createDbConfig(smartConfigFactory, user, pass, jdbcDriver = driver, maxPoolSize = poolsize)

        assertThat(createdConfig.hasPath(DatabaseConfig.JDBC_URL)).isFalse
    }

    @Test
    fun `when createDbConfig leave default poolsize empty`() {
        val createdConfig = createDbConfig(smartConfigFactory, user, pass, jdbcUrl = url, jdbcDriver = driver, )

        assertThat(createdConfig.hasPath(DatabaseConfig.DB_POOL_MAX_SIZE)).isFalse
    }
}