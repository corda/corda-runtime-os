package net.corda.configuration.write.impl.tests

import net.corda.configuration.write.ConfigWriteServiceException
import net.corda.configuration.write.impl.BootstrapConfigEvent
import net.corda.configuration.write.impl.ConfigWriteEventHandler
import net.corda.configuration.write.impl.writer.RPCSubscriptionFactory
import net.corda.configuration.write.publish.ConfigPublishService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus.DOWN
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StopEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of [ConfigWriteEventHandler]. */
class ConfigWriteEventHandlerTests {
    private lateinit var eventHandler: ConfigWriteEventHandler
    private lateinit var rpcSubscriptionFactory: RPCSubscriptionFactory

    private lateinit var configMerger: ConfigMerger

    @BeforeEach
    fun setUp() {
        rpcSubscriptionFactory = mock()
        configMerger = mock()
        eventHandler = ConfigWriteEventHandler(rpcSubscriptionFactory, configMerger)
    }

    @Test
    fun `BootstrapConfigEvent sets boot config and follows needed components`() {
        val coordinator = mock<LifecycleCoordinator>()
        val bootstrapConfigEvent = mock<BootstrapConfigEvent>()

        eventHandler.processEvent(bootstrapConfigEvent, coordinator)
        verify(bootstrapConfigEvent).bootstrapConfig
        verify(coordinator).followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
                LifecycleCoordinatorName.forComponent<ConfigPublishService>()
            )
        )
    }

    @Test
    fun `RegistrationStatusChangeEvent UP event sets up handler`() {
        val bootstrapConfig = mock<SmartConfig>()
        val coordinator = mock<LifecycleCoordinator>()

        // First set up bootConfig to event handler
        val bootstrapConfigEvent = mock<BootstrapConfigEvent>()
        whenever(bootstrapConfigEvent.bootstrapConfig).thenReturn(bootstrapConfig)
        eventHandler.processEvent(bootstrapConfigEvent, coordinator)

        whenever(configMerger.getMessagingConfig(bootstrapConfig)).thenReturn(bootstrapConfig)
        whenever(rpcSubscriptionFactory.create(bootstrapConfig)).thenReturn(mock())

        val event = mock<RegistrationStatusChangeEvent>().also {
            whenever(it.status).thenReturn(UP)
        }
        eventHandler.processEvent(event, coordinator)

        verify(coordinator).updateStatus(eq(UP), any())
    }

    @Test
    fun `Throws if BootstrapConfigEvent event received twice`() {
        eventHandler.processEvent(BootstrapConfigEvent(mock()), mock())

        assertThrows<ConfigWriteServiceException> {
            eventHandler.processEvent(BootstrapConfigEvent(mock()), mock())
        }
    }

    @Test
    fun `StopEvent sets coordinator status to down`() {
        val coordinator = mock<LifecycleCoordinator>()
        eventHandler.processEvent(StopEvent(), coordinator)

        verify(coordinator).updateStatus(eq(DOWN), any())
    }
}