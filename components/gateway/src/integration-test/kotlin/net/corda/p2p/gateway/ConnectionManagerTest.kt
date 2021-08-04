package net.corda.p2p.gateway

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.ConnectionManager
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.http.HttpConnectionEvent
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.http.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.FileInputStream
import java.net.SocketAddress
import java.net.URI
import java.security.KeyStore
import java.util.concurrent.CountDownLatch

class ConnectionManagerTest {

    private val clientMessageContent = "PING"
    private val serverResponseContent = "PONG"
    private val keystorePass = "cordacadevpass"
    private val truststorePass = "trustpass"
    private val serverAddresses = listOf("http://localhost:10000", "http://localhost:10001")
    private val sslConfiguration = object : SslConfiguration {
        override val keyStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("sslkeystore.jks")!!.file), keystorePass.toCharArray())
        }
        override val keyStorePassword: String = keystorePass
        override val trustStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("truststore.jks")!!.file), truststorePass.toCharArray())
        }
        override val trustStorePassword: String = truststorePass
    }

    @Test
    @Timeout(30)
    fun `acquire connection`() {
        val manager = ConnectionManager(sslConfiguration, ConnectionConfiguration())
        manager.start()
        val (host, port) = URI.create(serverAddresses.first()).let { Pair(it.host, it.port) }
        HttpServer(host, port, sslConfiguration).use { server ->
            server.addListener(object : HttpEventListener {
                override fun onMessage(message: HttpMessage) {
                    assertEquals(clientMessageContent, String(message.payload))
                    server.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(), message.source)
                }
            })
            server.start()
            manager.acquire(URI.create(serverAddresses.first().toString())).use { client ->
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
    fun `reuse connection`() {
        val manager = ConnectionManager(sslConfiguration,  ConnectionConfiguration())
        manager.start()
        val requestReceived = CountDownLatch(2)
        val serverURI = URI.create((serverAddresses.first()))
        HttpServer(serverURI.host, serverURI.port, sslConfiguration).use { server ->
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

            manager.acquire(serverURI).write(clientMessageContent.toByteArray())
            manager.acquire(serverURI).write(clientMessageContent.toByteArray())
            requestReceived.await()
            assertEquals(1, remotePeers.size)
            manager.acquire(serverURI).stop()
        }
    }
}