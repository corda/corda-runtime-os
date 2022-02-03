import net.corda.db.connection.manager.impl.BootstrapConfigProvided
import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.impl.DbConnectionManagerEventHandler
import net.corda.db.connection.manager.impl.DbConnectionsRepositoryImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DbConnectionManagerEventHandlerTest {
    private val dbConnectionsRepository = mock< DbConnectionsRepositoryImpl>()
    private val configFactory = mock<SmartConfigFactory>()
    private val config = mock<SmartConfig>() {
        on { this.factory }.doReturn(configFactory)
    }
    private val coordinator = mock<LifecycleCoordinator>()

    @Test
    fun `when bootstrap initialise connections repo`() {
        val eventHandler = DbConnectionManagerEventHandler(dbConnectionsRepository)

        eventHandler.processEvent(BootstrapConfigProvided(config), coordinator)

        verify(dbConnectionsRepository).initialise(config)
    }

    @Test
    fun `when bootstrap initialised report UP`() {
        val eventHandler = DbConnectionManagerEventHandler(dbConnectionsRepository)

        eventHandler.processEvent(BootstrapConfigProvided(config), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `when boostrap twice throw`() {
        val eventHandler = DbConnectionManagerEventHandler(dbConnectionsRepository)

        eventHandler.processEvent(BootstrapConfigProvided(config), coordinator)

        assertThrows<DBConfigurationException> {
            eventHandler.processEvent(BootstrapConfigProvided(config), coordinator)
        }
    }
}