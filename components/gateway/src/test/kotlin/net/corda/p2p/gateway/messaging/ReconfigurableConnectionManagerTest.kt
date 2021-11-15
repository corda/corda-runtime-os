package net.corda.p2p.gateway.messaging

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.StartEvent
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.net.URI

class ReconfigurableConnectionManagerTest {
    private val manager = mock<ConnectionManager>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { start() } doAnswer {
            handler.lastValue.processEvent(StartEvent(), mock)
        }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val service = mock<ConfigurationReadService> {
        on { registerForUpdates(any()) } doReturn mock()
    }
    private val configuration = mock<GatewayConfiguration> {
        on { sslConfig } doReturn mock()
        on { connectionConfig } doReturn mock()
    }

    private val connectionManager = ReconfigurableConnectionManager(factory, service) { _, _ -> manager }

    @Test
    fun `acquire will throw an exception if configuration is not ready`() {
        assertThrows<IllegalStateException> {
            connectionManager.acquire(DestinationInfo(URI("http://www.r3.com:3000"), "", null))
        }
    }

    @Test
    fun `acquire will call the manager`() {
        connectionManager.start()
        connectionManager.applyNewConfiguration(configuration, null)
        val info = DestinationInfo(
            URI("http://www.r3.com:3000"),
            "",
            null
        )

        connectionManager
            .acquire(
                info
            )

        verify(manager).acquire(info)
    }

    @Test
    fun `applyNewConfiguration will close manager`() {
        connectionManager.start()
        connectionManager.applyNewConfiguration(configuration, null)

        val secondConfiguration = mock<GatewayConfiguration> {
            on { sslConfig } doReturn mock()
            on { connectionConfig } doReturn mock()
        }
        connectionManager.applyNewConfiguration(secondConfiguration, configuration)

        verify(manager).close()
    }

    @Test
    fun `applyNewConfiguration will not close the manager if same configuration`() {
        val sslConfiguration = mock<SslConfiguration>()
        val connectionConfiguration = mock<ConnectionConfiguration>()
        val firstConfiguration = mock<GatewayConfiguration> {
            on { sslConfig } doReturn sslConfiguration
            on { connectionConfig } doReturn connectionConfiguration
        }
        val secondConfiguration = mock<GatewayConfiguration> {
            on { sslConfig } doReturn sslConfiguration
            on { connectionConfig } doReturn connectionConfiguration
        }
        connectionManager.start()
        connectionManager.applyNewConfiguration(firstConfiguration, null)

        connectionManager.applyNewConfiguration(secondConfiguration, secondConfiguration)

        verify(manager, never()).close()
    }

    @Test
    fun `close will close the manager`() {
        connectionManager.start()
        connectionManager.applyNewConfiguration(configuration, null)

        connectionManager.close()

        verify(manager).close()
    }
}
