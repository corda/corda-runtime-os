import com.typesafe.config.ConfigFactory
import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DEFAULT_JDBC_URL
import net.corda.db.connection.manager.createDbConfig
import net.corda.db.connection.manager.createFromConfig
import net.corda.db.core.DataSourceFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.secret.SecretsLookupService
import net.corda.schema.configuration.ConfigDefaults
import net.corda.schema.configuration.ConfigKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DbConfigTest {
    private val dataSourceFactory =  mock<DataSourceFactory>()
    private val secretConfig = SmartConfigImpl(ConfigFactory.parseMap(mapOf(
        "secret" to "secret value"
    )), mock(), mock())
    private val smartConfigFactory = mock<SmartConfigFactory>() {
        on { makeSecret(any()) }.doReturn(secretConfig)
    }
    private val secretsLookupService = mock<SecretsLookupService>()

    private val user = "user"
    private val pass = "pass"
    private val driver = "driver"
    private val url = "url"
    private val poolsize = 987
    private val fullConfig = """
        ${ConfigKeys.Companion.JDBC_DRIVER}=$driver
        ${ConfigKeys.Companion.JDBC_URL}=$url
        ${ConfigKeys.Companion.DB_POOL_MAX_SIZE}=$poolsize
        ${ConfigKeys.Companion.DB_USER}=$user
        ${ConfigKeys.Companion.DB_PASS}=$pass
    """.trimIndent()
    private val fullSmartConfig = SmartConfigImpl(
        ConfigFactory.parseString(fullConfig),
        smartConfigFactory,
        secretsLookupService
    )

    private val minimalConfig = """
        ${ConfigKeys.Companion.DB_USER}=user
        ${ConfigKeys.Companion.DB_PASS}=pass
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
            ConfigDefaults.JDBC_DRIVER,
            DEFAULT_JDBC_URL,
            user,
            pass,
            false,
            ConfigDefaults.DB_POOL_MAX_SIZE)
    }

    @Test
    fun `when username missing throw`() {
        assertThrows<DBConfigurationException> {
            dataSourceFactory.createFromConfig(SmartConfigImpl(
                ConfigFactory.parseString("${ConfigKeys.Companion.DB_PASS}=pass"),
                mock(),
                mock()))
        }
    }

    @Test
    fun `when pass missing throw`() {
        assertThrows<DBConfigurationException> {
            dataSourceFactory.createFromConfig(SmartConfigImpl(
                ConfigFactory.parseString("${ConfigKeys.Companion.DB_PASS}=user"),
                mock(),
                mock()))
        }
    }

    @Test
    fun `when createDbConfig can be read`() {
        val createdConfig = createDbConfig(smartConfigFactory, user, pass, driver, url, poolsize)

        assertThat(createdConfig.getString(ConfigKeys.DB_USER)).isEqualTo(user)
        assertThat(createdConfig.getConfig(ConfigKeys.DB_PASS)).isEqualTo(secretConfig)
        assertThat(createdConfig.getString(ConfigKeys.DB_PASS + ".secret")).isEqualTo("secret value")
        assertThat(createdConfig.getString(ConfigKeys.JDBC_DRIVER)).isEqualTo(driver)
        assertThat(createdConfig.getString(ConfigKeys.JDBC_URL)).isEqualTo(url)
        assertThat(createdConfig.getInt(ConfigKeys.DB_POOL_MAX_SIZE)).isEqualTo(poolsize)
    }

    @Test
    fun `when createDbConfig leave default driver empty`() {
        val createdConfig = createDbConfig(smartConfigFactory, user, pass, jdbcUrl = url, maxPoolSize = poolsize)

        assertThat(createdConfig.hasPath(ConfigKeys.JDBC_DRIVER)).isFalse
    }

    @Test
    fun `when createDbConfig leave default url empty`() {
        val createdConfig = createDbConfig(smartConfigFactory, user, pass, jdbcDriver = driver, maxPoolSize = poolsize)

        assertThat(createdConfig.hasPath(ConfigKeys.JDBC_URL)).isFalse
    }

    @Test
    fun `when createDbConfig leave default poolsize empty`() {
        val createdConfig = createDbConfig(smartConfigFactory, user, pass, jdbcUrl = url, jdbcDriver = driver, )

        assertThat(createdConfig.hasPath(ConfigKeys.DB_POOL_MAX_SIZE)).isFalse
    }
}