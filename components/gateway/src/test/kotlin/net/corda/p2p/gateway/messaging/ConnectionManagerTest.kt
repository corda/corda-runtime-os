package net.corda.p2p.gateway.messaging

import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.concurrent.Future
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.StartEvent
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock

class ConnectionManagerTest {
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
    private val listener = mock<HttpEventListener>()
    private val condition = mock<Condition>()
    private val lock = mock<Lock> {
        on { newCondition() } doReturn condition
    }
    private val configuration = mock<GatewayConfiguration> {
        on { sslConfig } doReturn mock()
    }

    private val connectionManager = ConnectionManager(factory, service, listener, lock)

    @Test
    fun `acquire will throw an exception if configuration is not ready on time`() {
        doReturn(false).whenever(condition).await(any(), any())

        assertThrows<IllegalStateException> {
            connectionManager.acquire(DestinationInfo(URI("http://www.r3.com:3000"), "", null))
        }
    }

    @Test
    fun `acquire will wait for configuration`() {
        connectionManager.start()
        whenever(condition.await(any(), any())).thenAnswer {
            connectionManager.applyNewConfiguration(configuration, null)
            true
        }

        connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "",
                    null
                )
            )
            .close()

        verify(condition).await(any(), any())
    }

    @Test
    fun `acquire will not wait for if configuration is ready`() {
        connectionManager.start()
        connectionManager.applyNewConfiguration(configuration, null)

        connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "",
                    null
                )
            )
            .close()

        verify(condition, never()).await(any(), any())
    }

    @Test
    fun `acquire will not wait for if configuration is ready after the lock`() {
        connectionManager.start()
        val called = AtomicBoolean(false)
        whenever(lock.lock()).thenAnswer {
            if (!called.getAndSet(true)) {
                connectionManager.applyNewConfiguration(configuration, null)
            }
        }

        connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "",
                    null
                )
            )
            .close()

        verify(condition, never()).await(any(), any())
    }

    @Test
    fun `acquire returns a running client`() {
        connectionManager.start()
        connectionManager.applyNewConfiguration(configuration, null)

        val client = connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "",
                    null
                )
            )

        assertThat(client.isRunning).isTrue
    }

    @Test
    fun `second acquire with the same URL return the same client`() {
        connectionManager.start()
        connectionManager.applyNewConfiguration(configuration, null)

        val client1 = connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "1",
                    null
                )
            )
        val client2 = connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "2",
                    null
                )
            )

        assertThat(client1).isSameAs(client2)
    }

    @Test
    fun `second acquire with different URL return a new client`() {
        connectionManager.start()
        connectionManager.applyNewConfiguration(configuration, null)

        val client1 = connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "",
                    null
                )
            )
        val client2 = connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3001"),
                    "",
                    null
                )
            )

        assertThat(client1).isNotSameAs(client2)
    }

    @Test
    fun `applyNewConfiguration will close the clients`() {
        connectionManager.start()
        connectionManager.applyNewConfiguration(configuration, null)

        val client = connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "",
                    null
                )
            )

        val secondConfiguration = mock<GatewayConfiguration> {
            on { sslConfig } doReturn mock()
        }
        connectionManager.applyNewConfiguration(secondConfiguration, configuration)

        assertThat(client.isRunning).isFalse
    }

    @Test
    fun `applyNewConfiguration will clear the pool the clients`() {
        connectionManager.start()
        connectionManager.applyNewConfiguration(configuration, null)
        val client1 = connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "",
                    null
                )
            )

        val secondConfiguration = mock<GatewayConfiguration> {
            on { sslConfig } doReturn mock()
        }
        connectionManager.applyNewConfiguration(secondConfiguration, configuration)

        val client2 = connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "",
                    null
                )
            )
        assertThat(client1).isNotSameAs(client2)
    }

    @Test
    fun `applyNewConfiguration will not close the client if same configuration`() {
        val sslConfiguration = mock<SslConfiguration>()
        val firstConfiguration = mock<GatewayConfiguration> {
            on { sslConfig } doReturn sslConfiguration
        }
        val secondConfiguration = mock<GatewayConfiguration> {
            on { sslConfig } doReturn sslConfiguration
        }
        connectionManager.start()
        connectionManager.applyNewConfiguration(firstConfiguration, null)
        val client1 = connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "",
                    null
                )
            )

        connectionManager.applyNewConfiguration(secondConfiguration, secondConfiguration)
        val client2 = connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "",
                    null
                )
            )

        assertThat(client1).isSameAs(client2)
    }

    @Test
    fun `close will close the clients`() {
        val connectionManager = ConnectionManager(factory, service, listener, lock) { mock() }
        connectionManager.start()
        connectionManager.applyNewConfiguration(configuration, null)
        val client = connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "",
                    null
                )
            )

        connectionManager.close()

        assertThat(client.isRunning).isFalse
    }

    @Test
    fun `close will close the event loops`() {
        val terminationFuture = mock<Future<*>>()
        val loop = mock<NioEventLoopGroup> {
            on { terminationFuture() } doReturn terminationFuture
        }
        val connectionManager = ConnectionManager(factory, service, listener, lock) { loop }
        connectionManager.start()
        connectionManager.close()

        verify(loop, times(2)).shutdownGracefully()
        verify(terminationFuture, times(2)).sync()
    }
}
