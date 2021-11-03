package net.corda.p2p.gateway.messaging

import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.concurrent.Future
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.net.URI

class ConnectionManagerTest {
    private val sslConfiguration = mock<SslConfiguration>()
    private val connectionConfiguration = ConnectionConfiguration(10, 1.seconds, 1.seconds, 1.seconds, 1.seconds)

    private val connectionManager = ConnectionManager(sslConfiguration, connectionConfiguration)
    private val mockedClient = mockConstruction(HttpClient::class.java)

    @AfterEach
    fun cleanUp() {
        mockedClient.close()
    }

    @Test
    fun `acquire returns a running client`() {
        val client = connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "",
                    null
                )
            )

        assertThat(client).isEqualTo(mockedClient.constructed().first())
        verify(mockedClient.constructed().first(), times(1)).start()
    }

    @Test
    fun `second acquire with the same URL return the same client`() {
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
        assertThat(mockedClient.constructed()).hasSize(1)
        verify(mockedClient.constructed().first(), times(1)).start()
    }

    @Test
    fun `second acquire with different URL return a new client`() {
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
    fun `close will clear the pool the clients`() {
        val terminationFuture = mock<Future<*>>()
        val loop = mock<NioEventLoopGroup> {
            on { terminationFuture() } doReturn terminationFuture
        }
        val connectionManager = ConnectionManager(sslConfiguration, connectionConfiguration) { loop }
        val client1 = connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "",
                    null
                )
            )

        connectionManager.close()

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
    fun `close will close the clients`() {
        val terminationFuture = mock<Future<*>>()
        val loop = mock<NioEventLoopGroup> {
            on { terminationFuture() } doReturn terminationFuture
        }
        val connectionManager = ConnectionManager(sslConfiguration, connectionConfiguration) { loop }
        connectionManager
            .acquire(
                DestinationInfo(
                    URI("http://www.r3.com:3000"),
                    "",
                    null
                )
            )

        connectionManager.close()
        verify(mockedClient.constructed().first(), times(1)).stop()
    }

    @Test
    fun `close will close the event loops`() {
        val terminationFuture = mock<Future<*>>()
        val loop = mock<NioEventLoopGroup> {
            on { terminationFuture() } doReturn terminationFuture
        }
        val connectionManager = ConnectionManager(sslConfiguration, connectionConfiguration) { loop }
        connectionManager.close()

        verify(loop, times(2)).shutdownGracefully()
        verify(terminationFuture, times(2)).sync()
    }
}
