package net.corda.db.connection.manager.impl.tests

import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.impl.BootstrapConfigProvided
import net.corda.db.connection.manager.impl.DbConnectionManagerEventHandler
import net.corda.db.connection.manager.impl.DbConnectionManagerImpl
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DbConnectionManagerEventHandlerTest {
    private val dbConnectionsManager = mock<DbConnectionManagerImpl>()
    private val configFactory = mock<SmartConfigFactory>()
    private val config = mock<SmartConfig>() {
        on { this.factory }.doReturn(configFactory)
    }
    private val differentConfig = mock<SmartConfig>() {
        on { this.factory }.doReturn(configFactory)
    }
    private val coordinator = mock<LifecycleCoordinator>()

    @Test
    fun `when bootstrap initialise connections repo`() {
        val eventHandler = DbConnectionManagerEventHandler(dbConnectionsManager)

        eventHandler.processEvent(BootstrapConfigProvided(config), coordinator)

        verify(dbConnectionsManager).initialise(config)
    }

    @Test
    fun `when bootstrap initialised report UP`() {
        val eventHandler = DbConnectionManagerEventHandler(dbConnectionsManager)

        eventHandler.processEvent(BootstrapConfigProvided(config), coordinator)

        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `when boostrap twice with same configuration don't throw`() {
        val eventHandler = DbConnectionManagerEventHandler(dbConnectionsManager)

        eventHandler.processEvent(BootstrapConfigProvided(config), coordinator)

        assertDoesNotThrow {
            eventHandler.processEvent(BootstrapConfigProvided(config), coordinator)
        }
    }

    @Test
    fun `when boostrap twice with different configuration throw`() {
        val eventHandler = DbConnectionManagerEventHandler(dbConnectionsManager)

        eventHandler.processEvent(BootstrapConfigProvided(config), coordinator)

        assertThrows<DBConfigurationException> {
            eventHandler.processEvent(BootstrapConfigProvided(differentConfig), coordinator)
        }
    }
}
