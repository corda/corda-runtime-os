import com.typesafe.config.ConfigFactory
import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DEFAULT_JDBC_URL
import net.corda.db.connection.manager.createFromConfig
import net.corda.db.core.DataSourceFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.schema.configuration.ConfigDefaults
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import net.corda.schema.configuration.ConfigKeys

class DbConfigTest {
    private val dataSourceFactory =  mock<DataSourceFactory>()

    private val fullConfig = """
        ${ConfigKeys.Companion.JDBC_DRIVER}=driver
        ${ConfigKeys.Companion.JDBC_URL}=url
        ${ConfigKeys.Companion.DB_POOL_MAX_SIZE}=99
        ${ConfigKeys.Companion.DB_USER}=user
        ${ConfigKeys.Companion.DB_PASS}=pass
    """.trimIndent()
    private val fullSmartConfig = SmartConfigImpl(
        ConfigFactory.parseString(fullConfig),
        mock(),
        mock()
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
            "driver",
            "url",
            "user",
            "pass",
            false,
            99)
    }

    @Test
    fun `when default config return datasource`() {
        dataSourceFactory.createFromConfig(minimalSmartConfig)

        verify(dataSourceFactory).create(
            ConfigDefaults.JDBC_DRIVER,
            DEFAULT_JDBC_URL,
            "user",
            "pass",
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
}