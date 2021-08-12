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
            server.addListener(object : HttpEventListener {
                override fun onMessage(message: HttpMessage) {
                    assertEquals(clientMessageContent, String(message.payload))
                    server.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), message.source)
                }
            })
            server.start()
            HttpClient(DestinationInfo(serverAddress, aliceSNI[0], null), chipSslConfig, NioEventLoopGroup(1), NioEventLoopGroup(1)).use { client ->
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
                client.write(clientMessageContent.toByteArray(Charsets.UTF_8))
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
        val httpServer = HttpServer(serverAddress.host, serverAddress.port, aliceSslConfig)
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
                    val httpClient = HttpClient(DestinationInfo(serverAddress, aliceSNI[1], null), chipSslConfig, threadPool, threadPool)
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

        HttpServer(serverAddress.host, serverAddress.port, aliceSslConfig).use { server ->
            server.addListener(object : HttpEventListener {
                override fun onMessage(message: HttpMessage) {
                    assert(Arrays.equals(hugePayload, message.payload))
                    server.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), message.source)
                }
            })
            server.start()
            HttpClient(DestinationInfo(serverAddress, aliceSNI[0], null), bobSslConfig, NioEventLoopGroup(1), NioEventLoopGroup(1)).use { client ->
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

    @Test
    fun `tls handshake succeeds - revocation checking disabled C5`() {
        HttpServer(serverAddress.host, serverAddress.port, bobSslConfig).use { server ->
            server.start()
            HttpClient(DestinationInfo(serverAddress, bobSNI[0], null), aliceSslConfig, NioEventLoopGroup(1), NioEventLoopGroup(1)).use { client ->
                val connectedLatch = CountDownLatch(1)
                client.addListener(object : HttpEventListener {
                    override fun onOpen(event: HttpConnectionEvent) {
                        connectedLatch.countDown()
                    }
                })

                client.start()
                client.write(ByteArray(0))
                assert(connectedLatch.await(1, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun `tls handshake succeeds - revocation checking disabled C4`() {
        HttpServer(serverAddress.host, serverAddress.port, c4sslConfig).use { server ->
            server.start()
            HttpClient(DestinationInfo(serverAddress, partyASNI, partyAx500Name), c4sslConfig, NioEventLoopGroup(1), NioEventLoopGroup(1)).use { client ->
                val connectedLatch = CountDownLatch(1)
                client.addListener(object : HttpEventListener {
                    override fun onOpen(event: HttpConnectionEvent) {
                        connectedLatch.countDown()
                    }
                })

                client.start()
                client.write(ByteArray(0))
                assert(connectedLatch.await(1, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun `tls handshake fails - requested SNI is not recognized`() {
        HttpServer(serverAddress.host, serverAddress.port, aliceSslConfig).use { server ->
            server.start()
            HttpClient(DestinationInfo(serverAddress, bobSNI[0], null), chipSslConfig, NioEventLoopGroup(1), NioEventLoopGroup(1)).use { client ->
                val connectedLatch = CountDownLatch(1)
                client.addListener(object : HttpEventListener {
                    override fun onOpen(event: HttpConnectionEvent) {
                        connectedLatch.countDown()
                    }
                })

                client.start()
                client.write(ByteArray(0))
                connectedLatch.await(1, TimeUnit.SECONDS)
            }
        }

        loggingInterceptor.assertMessageExists(
            "Could not find a certificate matching the requested SNI value [hostname = ${bobSNI[0]}",
            Level.WARN
        )
    }

    @Test
    fun `tls handshake fails - server presents revoked certificate`() {
        HttpServer(serverAddress.host, serverAddress.port, bobSslConfig).use { server ->
            server.start()
            HttpClient(DestinationInfo(serverAddress, bobSNI[0], null), chipSslConfig, NioEventLoopGroup(1), NioEventLoopGroup(1)).use { client ->
                val connectedLatch = CountDownLatch(1)
                client.addListener(object : HttpEventListener {
                    override fun onOpen(event: HttpConnectionEvent) {
                        connectedLatch.countDown()
                    }
                })

                client.start()
                client.write(ByteArray(0))
                connectedLatch.await(1, TimeUnit.SECONDS)
            }
        }

        loggingInterceptor.assertMessageExists(
            "Bad certificate path PKIX path validation failed: java.security.cert.CertPathValidatorException: Certificate has been revoked",
            Level.ERROR
        )
    }
}