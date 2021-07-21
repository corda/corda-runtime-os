package net.corda.p2p.gateway.messaging.http

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.p2p.gateway.messaging.SslConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.FileInputStream
import java.net.URI
import java.security.KeyStore
import java.time.Instant
import java.util.Arrays
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class HttpTest {

    private val clientMessageContent = "PING"
    private val serverResponseContent = "PONG"
    private val keystorePass = "cordacadevpass"
    private val truststorePass = "trustpass"
    private val serverAddress = URI.create("http://localhost:10000")
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
    fun `simple client POST request`() {
        HttpServer(serverAddress.host, serverAddress.port, sslConfiguration).use { server ->
            server.onReceive.subscribe {
                assertEquals(clientMessageContent, String(it.payload))
                server.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), it.source)
            }
            server.start()
            HttpClient(serverAddress, sslConfiguration).use { client ->
                val clientReceivedResponses = CountDownLatch(1)
                client.onConnection.subscribe {
                    if (it.connected) {
                        client.send(clientMessageContent.toByteArray(Charsets.UTF_8))
                    }
                }
                var responseReceived = false
                client.onReceive.subscribe {
                    assertEquals(serverResponseContent, String(it.payload))
                    responseReceived = true
                    clientReceivedResponses.countDown()
                }
                client.start()
                clientReceivedResponses.await(5, TimeUnit.SECONDS)
                assertTrue(responseReceived)
            }

            Thread.sleep(10000)
        }
    }

    @Test
    fun `multiple clients multiple requests`() {
        val requestNo = 100
        val threadNo = 2
        val threads = mutableListOf<Thread>()
        val times = mutableListOf<Long>()
        val httpServer = HttpServer(serverAddress.host, serverAddress.port, sslConfiguration)
        httpServer.use { server ->
            server.onReceive.subscribe {
                assertEquals(clientMessageContent, String(it.payload))
                server.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), it.source)
            }
            server.start()
            repeat(threadNo) {
                val t = thread {
                    var startTime: Long = 0
                    var endTime: Long = 0
                    val httpClient = HttpClient(serverAddress, sslConfiguration)
                    val clientReceivedResponses = CountDownLatch(requestNo)
                    httpClient.use {
                        httpClient.onReceive.subscribe {
                            assertEquals(serverResponseContent, String(it.payload))
                            clientReceivedResponses.countDown()
                        }
                        httpClient.onConnection.subscribe {
                            if (it.connected) {
                                startTime = Instant.now().toEpochMilli()
                                repeat(requestNo) {
                                    httpClient.send(clientMessageContent.toByteArray(Charsets.UTF_8))
                                }
                            }
                            if (!it.connected) {
                                endTime = Instant.now().toEpochMilli()
                            }
                        }
                        httpClient.start()
                        clientReceivedResponses.await()
                    }
                    times.add(endTime - startTime)
                }
                threads.add(t)
            }
            threads.forEach { it.join() }
        }

        times.forEach {
            println("Client finished sending $requestNo requests in $it milliseconds")
        }
    }

    @Test
    fun `large payload`() {
        val hugePayload = FileInputStream(javaClass.classLoader.getResource("10mb.txt")!!.file).readAllBytes()

        HttpServer(serverAddress.host, serverAddress.port, sslConfiguration).use { server ->
            server.onReceive.subscribe {
                assert(Arrays.equals(hugePayload, it.payload))
                server.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), it.source)
            }
            server.start()
            HttpClient(serverAddress, sslConfiguration).use { client ->
                val clientReceivedResponses = CountDownLatch(1)
                client.onConnection.subscribe {
                    if (it.connected) {
                        client.send(hugePayload)
                    }
                }
                var responseReceived = false
                client.onReceive.subscribe {
                    assertEquals(serverResponseContent, String(it.payload))
                    responseReceived = true
                    clientReceivedResponses.countDown()
                }
                client.start()
                clientReceivedResponses.await()
                assertTrue(responseReceived)
            }
        }
    }
}