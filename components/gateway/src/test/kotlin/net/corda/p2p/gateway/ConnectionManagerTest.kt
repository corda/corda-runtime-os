package net.corda.p2p.gateway

import net.corda.p2p.gateway.messaging.ConnectionManager
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.v5.base.util.NetworkHostAndPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.FileInputStream
import java.security.KeyStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeoutException

class ConnectionManagerTest {

    private val clientMessageContent = "PING"
    private val serverResponseContent = "PONG"
    private val keystorePass = "cordacadevpass"
    private val truststorePass = "trustpass"
    private val serverAddresses = listOf(NetworkHostAndPort.parse("localhost:10000"), NetworkHostAndPort.parse("localhost:10001"))
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
        val manager = ConnectionManager(sslConfiguration)
        HttpServer(serverAddresses.first(), sslConfiguration).use { server ->
            server.onReceive.subscribe {
                assertEquals(clientMessageContent, String(it.payload))
                server.write(serverResponseContent.toByteArray(), it.source!!)
            }
            server.start()
            manager.acquire(serverAddresses.first()).use { client ->
                // Client is connected at this point
                val responseReceived = CountDownLatch(1)
                client.onReceive.subscribe {
                    assertEquals(serverResponseContent, String(it))
                    responseReceived.countDown()
                }
                client.send(clientMessageContent.toByteArray())
                responseReceived.await()
            }
        }
    }

    @Test
    fun `reuse connection`() {
        val manager = ConnectionManager(sslConfiguration)
        HttpServer(serverAddresses.first(), sslConfiguration).use { server ->
            val remotePeers = mutableListOf<NetworkHostAndPort>()
            server.onConnection.subscribe {
                if (it.connected) {
                    remotePeers.add(it.remoteAddress)
                }
            }
            server.start()

            manager.acquire(serverAddresses.first())
            assertEquals( 1, manager.activeConnectionsForHost(serverAddresses.first()))
            manager.acquire(serverAddresses.first())
            assertEquals( 1, manager.activeConnectionsForHost(serverAddresses.first()))
            assertEquals(1, remotePeers.size)
            manager.acquire(serverAddresses.first()).stop()
        }
    }

    @Test
    fun `acquire times out`() {
        var gotException = false
        try {
            ConnectionManager(sslConfiguration).acquire(serverAddresses.first())
        } catch (e: Exception) {
            assert(e is TimeoutException)
            println(e.message)
            gotException = true
        }
        assert(gotException)
    }
}