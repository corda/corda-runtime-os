package net.corda.p2p.gateway.messaging.http

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.p2p.gateway.LoggingInterceptor
import net.corda.p2p.gateway.TestBase
import org.apache.logging.log4j.Level
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.FileInputStream
import java.net.URI
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.ssl.SslHandler
import java.security.SecureRandom
import java.time.Instant
import java.util.Arrays
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509ExtendedKeyManager
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import net.corda.lifecycle.Lifecycle
import net.corda.p2p.NetworkType
import net.corda.p2p.gateway.messaging.SslConfiguration
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.Timeout

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

    private val serverAddress = URI.create("http://alice.net:10000")

    @Test
    @Timeout(30)
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
                clientReceivedResponses.await()
                assertTrue(responseReceived)
            }
        }
    }

    @Test
    @Timeout(30)
    fun `multiple clients multiple requests`() {
        val requestNo = 10
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
    @Timeout(30)
    fun `large payload`() {
        val hugePayload = FileInputStream(javaClass.classLoader.getResource("10mb.txt")!!.file).readAllBytes()

        HttpServer(serverAddress.host, serverAddress.port, aliceSslConfig).use { server ->
            server.addListener(object : HttpEventListener {
                override fun onMessage(message: HttpMessage) {
                    assertTrue(Arrays.equals(hugePayload, message.payload))
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
    @Timeout(30)
    fun `tls handshake succeeds - revocation checking disabled C5`() {
        HttpServer(serverAddress.host, serverAddress.port, bobSslConfig).use { server ->
            server.start()
            HttpClient(DestinationInfo(serverAddress, bobSNI[0], null), aliceSslConfig, NioEventLoopGroup(1), NioEventLoopGroup(1)).use { client ->
                var connected = false
                val connectedLatch = CountDownLatch(1)
                client.addListener(object : HttpEventListener {
                    override fun onOpen(event: HttpConnectionEvent) {
                        connected = true
                        connectedLatch.countDown()
                    }
                })

                client.start()
                client.write(ByteArray(0))
                connectedLatch.await()
                assertTrue(connected)
            }
        }
    }

    @Test
    @Timeout(30)
    fun `tls handshake succeeds - revocation checking disabled C4`() {
        HttpServer(serverAddress.host, serverAddress.port, c4sslConfig).use { server ->
            server.start()
            HttpClient(DestinationInfo(serverAddress, partyASNI, partyAx500Name), c4sslConfig, NioEventLoopGroup(1), NioEventLoopGroup(1)).use { client ->
                var connected = false
                val connectedLatch = CountDownLatch(1)
                client.addListener(object : HttpEventListener {
                    override fun onOpen(event: HttpConnectionEvent) {
                        connected = true
                        connectedLatch.countDown()
                    }
                })

                client.start()
                client.write(ByteArray(0))
                connectedLatch.await()
                assertTrue(connected)
            }
        }
    }

    @Test
    @Timeout(30)
    fun `tls handshake fails - server identity check fails C4`() {
        MitmServer(serverAddress.host, serverAddress.port, c4sslConfig).use { server ->
            server.start()
            val expectedX500Name = "O=Test,L=London,C=GB"
            val sni = SniCalculator.calculateSni("O=Test,L=London,C=GB", NetworkType.CORDA_4, serverAddress.host)
            HttpClient(DestinationInfo(serverAddress,  sni, X500Name(expectedX500Name)), c4sslConfig, NioEventLoopGroup(1), NioEventLoopGroup(1)).use { client ->
                val connectedLatch = CountDownLatch(1)
                client.addListener(object : HttpEventListener {
                    override fun onOpen(event: HttpConnectionEvent) {
                        connectedLatch.countDown()
                    }
                })

                client.start()
                client.write(ByteArray(0))
                // Check the connection didn't actually succeed; latch times out
                assertFalse(connectedLatch.await(1, TimeUnit.SECONDS))

                // Check HandshakeException is thrown and logged
                val expectedMessage = "Bad certificate identity or path. " +
                        "Certificate name doesn't match. Expected $expectedX500Name but received C=GB,L=London,O=PartyA"
                loggingInterceptor.assertMessageExists(expectedMessage, Level.ERROR)
            }
        }
    }

    @Test
    @Timeout(30)
    fun `tls handshake fails - server identity check fails C5`() {
        MitmServer(serverAddress.host, serverAddress.port, chipSslConfig).use { server ->
            server.start()
            HttpClient(DestinationInfo(serverAddress, aliceSNI[0], null), daleSslConfig, NioEventLoopGroup(1), NioEventLoopGroup(1)).use { client ->
                val connectedLatch = CountDownLatch(1)
                client.addListener(object : HttpEventListener {
                    override fun onOpen(event: HttpConnectionEvent) {
                        connectedLatch.countDown()
                    }
                })

                client.start()
                client.write(ByteArray(0))
                // Check the connection didn't actually succeed; latch times out
                assertFalse(connectedLatch.await(1, TimeUnit.SECONDS))

                // Check HandshakeException is thrown and logged
                val expectedMessage = "Bad certificate identity or path. " +
                        "No subject alternative DNS name matching ${serverAddress.host} found"
                loggingInterceptor.assertMessageExists(expectedMessage, Level.ERROR)
            }
        }
    }

    @Test
    @Timeout(30)
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
    @Timeout(30)
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
            "Bad certificate identity or path. PKIX path validation failed: " +
                    "java.security.cert.CertPathValidatorException: Certificate has been revoked",
            Level.ERROR
        )
    }

    // Lightweight testing server which ignores SNI checks and presents invalid certificates
    // This server is not meant to receive requests.
    private class MitmServer(private val host: String,
                             private val port: Int,
                             private val sslConfig: SslConfiguration) : Lifecycle {

        private val lock = ReentrantLock()
        private var bossGroup: EventLoopGroup? = null
        private var workerGroup: EventLoopGroup? = null
        private var serverChannel: Channel? = null

        private var started = false
        override val isRunning: Boolean
            get() = started

        override fun start() {
            lock.withLock {
                bossGroup = NioEventLoopGroup(1)
                workerGroup = NioEventLoopGroup(4)

                val server = ServerBootstrap()
                server.group(bossGroup, workerGroup).channel(NioServerSocketChannel::class.java)
                    .childHandler(ServerChannelInitializer(this))
                val channelFuture = server.bind(host, port).sync()
                serverChannel = channelFuture.channel()
                started = true
            }
        }

        override fun stop() {
            lock.withLock {
                try {
                    serverChannel?.close()
                    serverChannel = null

                    workerGroup?.shutdownGracefully()
                    workerGroup?.terminationFuture()?.sync()

                    bossGroup?.shutdownGracefully()
                    bossGroup?.terminationFuture()?.sync()

                    workerGroup = null
                    bossGroup = null
                } finally {
                    started = false
                }
            }
        }

        private class ServerChannelInitializer(private val parent: MitmServer) : ChannelInitializer<SocketChannel>() {

            private val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())

            init {
                parent.sslConfig.run {
                    keyManagerFactory.init(this.keyStore, this.keyStorePassword.toCharArray())
                }
            }

            override fun initChannel(ch: SocketChannel) {
                val pipeline = ch.pipeline()
                pipeline.addLast("sslHandler", createServerSslHandler(keyManagerFactory))
            }

            fun createServerSslHandler(keyManagerFactory: KeyManagerFactory): SslHandler {
                val sslContext = SSLContext.getInstance("TLS")
                val keyManagers = keyManagerFactory.keyManagers
                sslContext.init(arrayOf(keyManagers.first() as X509ExtendedKeyManager), null, SecureRandom())

                val sslEngine = sslContext.createSSLEngine().also {
                    it.useClientMode = false
                    it.needClientAuth = false
                    it.enabledProtocols = arrayOf(TLS_VERSION)
                    it.enabledCipherSuites = CIPHER_SUITES
                    it.enableSessionCreation = true
                }
                val sslHandler = SslHandler(sslEngine)
                sslHandler.handshakeTimeoutMillis = HANDSHAKE_TIMEOUT
                return sslHandler
            }
        }
    }
}