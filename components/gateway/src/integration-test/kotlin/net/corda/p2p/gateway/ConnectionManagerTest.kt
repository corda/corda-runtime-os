package net.corda.p2p.gateway

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.ConnectionManager
import net.corda.p2p.gateway.messaging.http.HttpConnectionEvent
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.http.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import net.corda.p2p.gateway.messaging.http.DestinationInfo

class ConnectionManagerTest : TestBase() {

    private val serverAddress = URI.create("http://localhost:10000")
    private val destination = DestinationInfo(serverAddress, aliceSNI[0], null)

    @Test
    @Timeout(30)
    fun `acquire connection`() {
        val manager = ConnectionManager(aliceSslConfig)
        manager.start()
        val (host, port) = serverAddress.let { Pair(it.host, it.port) }
        HttpServer(host, port, aliceSslConfig).use { server ->
            server.addListener(object : HttpEventListener {
                override fun onMessage(message: HttpMessage) {
                    assertEquals(clientMessageContent, String(message.payload))
                    server.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(), message.source)
                }
            })
            server.start()
            manager.acquire(destination).use { client ->
                // Client is connected at this point
                val responseReceived = CountDownLatch(1)
                client.addListener(object : HttpEventListener {
                    override fun onMessage(message: HttpMessage) {
                        assertEquals(serverResponseContent, String(message.payload))
                        responseReceived.countDown()
                    }
                })
                client.write(clientMessageContent.toByteArray())
                responseReceived.await()
            }
        }
    }

    @Test
    @Timeout(30)
    fun `reuse connection`() {
        val manager = ConnectionManager(aliceSslConfig)
        manager.start()
        val requestReceived = CountDownLatch(2)
        HttpServer(serverAddress.host, serverAddress.port, aliceSslConfig).use { server ->
            val remotePeers = mutableListOf<SocketAddress>()
            server.addListener(object : HttpEventListener {
                override fun onOpen(event: HttpConnectionEvent) {
                    remotePeers.add(event.channel.remoteAddress())
                }
                override fun onMessage(message: HttpMessage) {
                    requestReceived.countDown()
                }
            })
            server.start()
            manager.acquire(destination).write(clientMessageContent.toByteArray())
            manager.acquire(destination).write(clientMessageContent.toByteArray())
            requestReceived.await()
            assertEquals(1, remotePeers.size)
            manager.acquire(destination).stop()
        }
    }
}