package net.corda.p2p.gateway.messaging

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
    private val badConfigurationException = RuntimeException("Bad Config")
    private val badConfiguration = mock<GatewayConfiguration> {
        on { sslConfig } doThrow(badConfigurationException)
    }

    private val resourcesHolder = mock<ResourcesHolder>()
    private lateinit var configHandler: ReconfigurableConnectionManager.ConnectionManagerConfigChangeHandler
    private val dominoTile = Mockito.mockConstruction(ComplexDominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        configHandler = (context.arguments()[6] as ReconfigurableConnectionManager.ConnectionManagerConfigChangeHandler)
    }

    private val connectionManager = ReconfigurableConnectionManager(factory, service) { _, _ -> manager }

    @AfterEach
    fun cleanUp() {
        dominoTile.close()
    }

    @Test
    fun `acquire will throw an exception if configuration is not ready`() {
        assertThrows<IllegalStateException> {
            connectionManager.acquire(DestinationInfo(URI("http://www.r3.com:3000"), "", null, mock(), null))
        }
    }

    @Test
    fun `acquire will call the manager`() {
        val resources = ResourcesHolder()
        connectionManager.start()
        configHandler.applyNewConfiguration(configuration, null, resources)
        val info = DestinationInfo(
            URI("http://www.r3.com:3000"),
            "",
            null,
            mock(),
            null,
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
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder)

        val secondConfiguration = mock<GatewayConfiguration> {
            on { sslConfig } doReturn mock()
            on { connectionConfig } doReturn mock()
        }
        configHandler.applyNewConfiguration(secondConfiguration, configuration, resourcesHolder)

        verify(manager).close()
    }

    @Test
    fun `applyNewConfiguration completes configUpdateResult`() {
        val future = configHandler.applyNewConfiguration(configuration, null, resourcesHolder)

        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isFalse
    }

    @Test
    fun `applyNewConfiguration applyNewConfiguration completes configUpdateResult exceptionally if bad config`() {
        val future = configHandler.applyNewConfiguration(badConfiguration, null, resourcesHolder)

        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isTrue
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
        configHandler.applyNewConfiguration(firstConfiguration, null, resourcesHolder)

        configHandler.applyNewConfiguration(secondConfiguration, secondConfiguration, resourcesHolder)

        verify(manager, never()).close()
    }

    @Test
    fun `applyNewConfiguration keeps the manager in the resource holder`() {
        configHandler.applyNewConfiguration(configuration, null, resourcesHolder)

        verify(resourcesHolder).keep(manager)
    }
}
