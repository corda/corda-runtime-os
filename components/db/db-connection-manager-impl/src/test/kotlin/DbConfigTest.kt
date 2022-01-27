import com.typesafe.config.ConfigFactory
import net.corda.db.connection.manager.impl.CONFIG_DB_DRIVER
import net.corda.db.connection.manager.impl.CONFIG_DB_DRIVER_DEFAULT
import net.corda.db.connection.manager.impl.CONFIG_DB_PASS
import net.corda.db.connection.manager.impl.CONFIG_DB_USER
import net.corda.db.connection.manager.impl.CONFIG_JDBC_URL
import net.corda.db.connection.manager.impl.CONFIG_DB_JDBC_URL_DEFAULT
import net.corda.db.connection.manager.impl.CONFIG_DB_MAX_POOL_SIZE
import net.corda.db.connection.manager.impl.CONFIG_DB_MAX_POOL_SIZE_DEFAULT
import net.corda.db.connection.manager.impl.DBConfigurationException
import net.corda.db.connection.manager.impl.createFromConfig
import net.corda.db.core.DataSourceFactory
import net.corda.libs.configuration.SmartConfigImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DbConfigTest {
    private val dataSourceFactory =  mock<DataSourceFactory>()

    private val fullConfig = """
$CONFIG_DB_DRIVER=driver
$CONFIG_JDBC_URL=url
$CONFIG_DB_MAX_POOL_SIZE=99
$CONFIG_DB_USER=user
$CONFIG_DB_PASS=pass
    """.trimIndent()
    private val fullSmartConfig = SmartConfigImpl(
        ConfigFactory.parseString(fullConfig),
        mock(),
        mock()
    )

    private val minimalConfig = """
$CONFIG_DB_USER=user
$CONFIG_DB_PASS=pass
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
            CONFIG_DB_DRIVER_DEFAULT,
            CONFIG_DB_JDBC_URL_DEFAULT,
            "user",
            "pass",
            false,
            CONFIG_DB_MAX_POOL_SIZE_DEFAULT)
    }

    @Test
    fun `when username missing throw`() {
        assertThrows<DBConfigurationException> {
            dataSourceFactory.createFromConfig(SmartConfigImpl(
                ConfigFactory.parseString("$CONFIG_DB_PASS=pass"),
                mock(),
                mock()))
        }
    }

    @Test
    fun `when pass missing throw`() {
        assertThrows<DBConfigurationException> {
            dataSourceFactory.createFromConfig(SmartConfigImpl(
                ConfigFactory.parseString("$CONFIG_DB_USER=user"),
                mock(),
                mock()))
        }
    }
}