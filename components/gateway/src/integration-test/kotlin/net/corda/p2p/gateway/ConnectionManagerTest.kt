package net.corda.p2p.gateway

import io.netty.channel.ConnectTimeoutException
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.ConnectionManager
import net.corda.p2p.gateway.messaging.http.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CountDownLatch

class ConnectionManagerTest : TestBase() {

    private val serverAddress = URI.create("http://localhost:10000")

    @Test
    @Timeout(30)
    fun `acquire connection`() {
        val manager = ConnectionManager(aliceSslConfig, ConnectionConfiguration())
        val (host, port) = serverAddress.let { Pair(it.host, it.port) }
        HttpServer(host, port, aliceSslConfig).use { server ->
            server.onReceive.subscribe {
                assertEquals(clientMessageContent, String(it.payload))
                server.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(), it.source)
            }
            server.start()
            manager.acquire(serverAddress, aliceSNI[0]).use { client ->
                // Client is connected at this point
                val responseReceived = CountDownLatch(1)
                client.onReceive.subscribe {
                    assertEquals(serverResponseContent, String(it.payload))
                    responseReceived.countDown()
                }
                client.send(clientMessageContent.toByteArray())
                responseReceived.await()
            }
        }
    }

    @Test
    fun `reuse connection`() {
        val manager = ConnectionManager(aliceSslConfig,  ConnectionConfiguration())
        HttpServer(serverAddress.host, serverAddress.port, aliceSslConfig).use { server ->
            val remotePeers = mutableListOf<SocketAddress>()
            server.onConnection.subscribe {
                if (it.connected) {
                    remotePeers.add(it.remoteAddress)
                }
            }
            server.start()

            manager.acquire(serverAddress, aliceSNI[0])
            assertEquals( 1, manager.activeConnectionsForHost(serverAddress))
            manager.acquire(serverAddress, aliceSNI[0])
            assertEquals( 1, manager.activeConnectionsForHost(serverAddress))
            assertEquals(1, remotePeers.size)
            manager.acquire(serverAddress, aliceSNI[0]).stop()
        }
    }

    @Test
    fun `acquire times out`() {
        var gotException = false
        try {
            val config =  ConnectionConfiguration(10, 100, 1000)
            ConnectionManager(aliceSslConfig, config).acquire(serverAddress, aliceSNI[0])
        } catch (e: Exception) {
            assert(e is ConnectTimeoutException)
            gotException = true
        }
        assert(gotException)
    }
}