package net.corda.p2p.gateway.messaging.http

import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.p2p.gateway.LoggingInterceptor
import net.corda.p2p.gateway.TestBase
import org.apache.logging.log4j.Level
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.FileInputStream
import java.net.URI
import io.netty.channel.nio.NioEventLoopGroup
import net.corda.p2p.gateway.messaging.SslConfiguration
import java.security.KeyStore
import java.time.Instant
import java.util.Arrays
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class HttpTest : TestBase() {

    companion object {
        lateinit var loggingInterceptor: LoggingInterceptor

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
        }

        @AfterEach
        fun teardown() {
            loggingInterceptor.reset()
        }
    }

    private val serverAddress = URI.create("http://localhost:10000")

    @Test
    fun `simple client POST request`() {
        HttpServer(serverAddress.host, serverAddress.port, aliceSslConfig).use { server ->
            server.onReceive.subscribe {
                assertEquals(clientMessageContent, String(it.payload))
                server.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), it.source)
            }
            server.start()
            HttpClient(serverAddress, aliceSNI[0], chipSslConfig).use { client ->
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
        }
    }

    @Test
    fun `multiple clients multiple requests`() {
        val requestNo = 1000
        val threadNo = 2
        val threads = mutableListOf<Thread>()
        val times = mutableListOf<Long>()
        val httpServer = HttpServer(serverAddress.host, serverAddress.port, sslConfiguration)
        val threadPool = NioEventLoopGroup(threadNo)
        httpServer.use { server ->
            server.addListener(object : HttpEventListener {
                override fun onMessage(message: HttpMessage) {
                    assertEquals(clientMessageContent, String(message.payload))
                    server.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), message.source)
                }
            })
            server.start()
            repeat(threadNo) {
                val t = thread {
                    var startTime: Long = 0
                    val httpClient = HttpClient(serverAddress, sslConfiguration, threadPool, threadPool)
                    val clientReceivedResponses = CountDownLatch(requestNo)
                    httpClient.use {
                        val clientListener = object : HttpEventListener {
                            override fun onMessage(message: HttpMessage) {
                                assertEquals(serverResponseContent, String(message.payload))
                                clientReceivedResponses.countDown()
                            }

                            override fun onOpen(event: HttpConnectionEvent) {
                                startTime = Instant.now().toEpochMilli()
                            }

                            override fun onClose(event: HttpConnectionEvent) {
                                val endTime = Instant.now().toEpochMilli()
                                times.add(endTime - startTime)
                            }
                        }
                        httpClient.addListener(clientListener)
                        httpClient.start()

                        repeat(requestNo) {
                            httpClient.write(clientMessageContent.toByteArray(Charsets.UTF_8))
                        }

                        clientReceivedResponses.await()
                    }
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
            server.addListener(object : HttpEventListener {
                override fun onMessage(message: HttpMessage) {
                    assert(Arrays.equals(hugePayload, message.payload))
                    server.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), message.source)
                }
            })
            server.start()
            HttpClient(serverAddress, sslConfiguration, NioEventLoopGroup(1), NioEventLoopGroup(1)).use { client ->
                val clientReceivedResponses = CountDownLatch(1)
                var responseReceived = false
                val clientListener = object : HttpEventListener {
                    override fun onMessage(message: HttpMessage) {
                        assertEquals(serverResponseContent, String(message.payload))
                        responseReceived = true
                        clientReceivedResponses.countDown()
                    }
                }
                client.addListener(clientListener)
                client.start()
                client.write(hugePayload)
                clientReceivedResponses.await()
                assertTrue(responseReceived)
            }
        }
    }
}