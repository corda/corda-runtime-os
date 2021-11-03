package net.corda.p2p.gateway.messaging

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.StartEvent
import net.corda.p2p.gateway.Gateway.Companion.CONFIG_KEY
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.net.URI
import java.time.Duration

class ReconfigurableConnectionManagerTest {
    private val manager = mock<ConnectionManager>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val event = argumentCaptor<LifecycleEvent>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { start() } doAnswer {
            handler.lastValue.processEvent(StartEvent(), mock)
        }
        on { postEvent(event.capture()) } doAnswer {
            handler.lastValue.processEvent(event.lastValue, mock)
        }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }

    private val configHandler = argumentCaptor<ConfigurationHandler>()
    private val service = mock<ConfigurationReadService> {
        on { registerForUpdates(configHandler.capture()) } doReturn mock()
    }
    private val listener = mock<HttpEventListener>()
    private val configuration = mock<Config> {
        on {getInt(any())} doReturn 5
        on {getLong(any())} doReturn 5L
        on {getString(any())} doReturn ""
        on {getDuration(any())} doReturn Duration.ofMillis(1)
        on {getBoolean(any())} doReturn true
        on {getConfig(any())} doReturn this.mock
        on {getEnum(eq(RevocationConfigMode::class.java), any())} doReturn RevocationConfigMode.OFF
    }

    private val connectionManager = ReconfigurableConnectionManager(factory, service, listener) { manager }

    @Test
    fun `acquire will throw an exception if configuration is not ready`() {
        assertThrows<IllegalStateException> {
            connectionManager.acquire(DestinationInfo(URI("http://www.r3.com:3000"), "", null))
        }
    }

    @Test
    fun `acquire will call the manager`() {
        //val resources = ResourcesHolder()
        connectionManager.start()

        configHandler.lastValue.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to configuration))
        //connectionManager.applyNewConfiguration(configuration, null, resources)
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
    fun `Config update will close manager`() {
        connectionManager.start()
        configHandler.lastValue.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to configuration))

        val secondConfiguration = mock<Config> {
            on {getInt(any())} doReturn 66
            on {getLong(any())} doReturn 5L
            on {getString(any())} doReturn ""
            on {getDuration(any())} doReturn Duration.ofMillis(1)
            on {getBoolean(any())} doReturn true
            on {getConfig(any())} doReturn this.mock
            on {getEnum(eq(RevocationConfigMode::class.java), any())} doReturn RevocationConfigMode.SOFT_FAIL
        }
        configHandler.lastValue.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to secondConfiguration))

        verify(manager).close()
    }

    @Test
    fun `Config update will not close the manager if same configuration`() {
        connectionManager.start()
        configHandler.lastValue.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to configuration))

        configHandler.lastValue.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to configuration))

        verify(manager, never()).close()
    }

    @Test
    fun `close will close the manager`() {
        connectionManager.start()
        configHandler.lastValue.onNewConfiguration(setOf(CONFIG_KEY), mapOf(CONFIG_KEY to configuration))

        connectionManager.close()

        verify(manager).close()
    }
}
