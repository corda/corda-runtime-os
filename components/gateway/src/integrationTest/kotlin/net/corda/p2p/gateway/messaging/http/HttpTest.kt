package net.corda.p2p.gateway.messaging.http

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.ssl.SslHandler
import net.corda.lifecycle.Lifecycle
import net.corda.p2p.NetworkType
import net.corda.p2p.gateway.LoggingInterceptor
import net.corda.p2p.gateway.TestBase
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.test.util.eventually
import net.corda.v5.base.util.seconds
import org.apache.logging.log4j.Level
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.URI
import java.security.SecureRandom
import java.time.Instant
import java.util.Arrays
import java.util.concurrent.ExecutionException
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509ExtendedKeyManager
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

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
        val listener = object : ListenerWithServer() {
            override fun onRequest(request: HttpRequest) {
                assertEquals(clientMessageContent, String(request.payload))
                server?.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), request.source)
            }
        }
        HttpServer(
            listener,
            GatewayConfiguration(
                serverAddress.host,
                serverAddress.port,
                aliceSslConfig
            ),
        ).use { server ->
            listener.server = server
            server.startAndWaitForStarted()
            HttpClient(
                DestinationInfo(serverAddress, aliceSNI[0], null, truststoreKeyStore),
                chipSslConfig,
                NioEventLoopGroup(1),
                NioEventLoopGroup(1),
                ConnectionConfiguration(),
            ).use { client ->
                client.start()
                val response = client.write(clientMessageContent.toByteArray(Charsets.UTF_8)).get()
                assertThat(response.statusCode).isEqualTo(HttpResponseStatus.OK)
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
        val listener = object : ListenerWithServer() {
            override fun onRequest(request: HttpRequest) {
                assertEquals(clientMessageContent, String(request.payload))
                server?.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), request.source)
            }
        }
        val httpServer = HttpServer(
            listener,
            GatewayConfiguration(
                serverAddress.host,
                serverAddress.port,
                aliceSslConfig
            )
        )
        val threadPool = NioEventLoopGroup(threadNo)
        httpServer.use { server ->
            listener.server = server
            server.startAndWaitForStarted()
            repeat(threadNo) {
                val t = thread {
                    val httpClient = HttpClient(
                        DestinationInfo(serverAddress, aliceSNI[1], null, truststoreKeyStore),
                        chipSslConfig,
                        threadPool,
                        threadPool,
                        ConnectionConfiguration(),
                    )
                    httpClient.use {
                        httpClient.start()

                        val start = Instant.now().toEpochMilli()
                        val futures = (1..requestNo).map {
                            httpClient.write(clientMessageContent.toByteArray(Charsets.UTF_8))
                        }
                        val responses = futures.map { it.get() }
                        times.add(Instant.now().toEpochMilli() - start)

                        responses.forEach {
                            assertThat(it.statusCode).isEqualTo(HttpResponseStatus.OK)
                        }
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
        val hugePayload = (1..0xA00_000)
            .map {
                (it % 0xFF).toByte()
            }
            .toByteArray()
        val listener = object : ListenerWithServer() {
            override fun onRequest(request: HttpRequest) {
                assertTrue(Arrays.equals(hugePayload, request.payload))
                server?.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), request.source)
            }
        }

        HttpServer(
            listener,
            GatewayConfiguration(
                serverAddress.host,
                serverAddress.port,
                aliceSslConfig
            )
        ).use { server ->
            listener.server = server
            server.startAndWaitForStarted()
            HttpClient(
                DestinationInfo(serverAddress, aliceSNI[0], null, truststoreKeyStore),
                bobSslConfig,
                NioEventLoopGroup(1),
                NioEventLoopGroup(1),
                ConnectionConfiguration(),
            ).use { client ->
                client.start()
                val response = client.write(hugePayload).get()
                assertThat(response.statusCode).isEqualTo(HttpResponseStatus.OK)
                assertThat(String(response.payload)).isEqualTo(serverResponseContent)
            }
        }
    }

    @Test
    @Timeout(30)
    fun `tls handshake succeeds - revocation checking disabled C5`() {
        val listener = object : ListenerWithServer() {
            override fun onRequest(request: HttpRequest) {
                server?.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), request.source)
            }
        }

        HttpServer(
            listener,
            GatewayConfiguration(
                serverAddress.host,
                serverAddress.port,
                bobSslConfig
            )
        ).use { server ->
            listener.server = server
            server.startAndWaitForStarted()
            HttpClient(
                DestinationInfo(serverAddress, bobSNI[0], null, truststoreKeyStore),
                aliceSslConfig,
                NioEventLoopGroup(1),
                NioEventLoopGroup(1),
                ConnectionConfiguration(),
            ).use { client ->
                client.start()
                val response = client.write(ByteArray(0)).get()
                assertThat(response.statusCode).isEqualTo(HttpResponseStatus.OK)
            }
        }
    }

    @Test
    @Timeout(30)
    fun `tls handshake succeeds - revocation checking disabled C4`() {
        val listener = object : ListenerWithServer() {
            override fun onRequest(request: HttpRequest) {
                server?.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), request.source)
            }
        }

        HttpServer(
            listener,
            GatewayConfiguration(
                serverAddress.host,
                serverAddress.port,
                c4sslConfig
            )
        ).use { server ->
            listener.server = server
            server.startAndWaitForStarted()
            HttpClient(
                DestinationInfo(serverAddress, partyASNI, partyAx500Name, c4TruststoreKeyStore),
                c4sslConfig,
                NioEventLoopGroup(1),
                NioEventLoopGroup(1),
                ConnectionConfiguration(),
            ).use { client ->
                client.start()
                val response = client.write(ByteArray(0)).get()
                assertThat(response.statusCode).isEqualTo(HttpResponseStatus.OK)
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
            HttpClient(
                DestinationInfo(serverAddress, sni, X500Name(expectedX500Name), c4TruststoreKeyStore),
                c4sslConfig,
                NioEventLoopGroup(1),
                NioEventLoopGroup(1),
                ConnectionConfiguration(),
            ).use { client ->

                client.start()
                val future = client.write(ByteArray(0))

                assertThatThrownBy {
                    future.get()
                }.isInstanceOf(ExecutionException::class.java)
                    .hasCauseInstanceOf(RuntimeException::class.java)
                    .hasStackTraceContaining("Connection was closed.")

                // Check HandshakeException is thrown and logged
                val expectedMessage = "Bad certificate identity or path. " +
                    "Certificate name doesn't match. Expected $expectedX500Name but received C=GB,L=London,O=PartyA"
                eventually {
                    loggingInterceptor.assertMessageExists(expectedMessage, Level.ERROR)
                }
            }
        }
    }

    @Test
    @Timeout(30)
    fun `tls handshake fails - server identity check fails C5`() {
        MitmServer(serverAddress.host, serverAddress.port, chipSslConfig).use { server ->
            server.start()
            HttpClient(
                DestinationInfo(serverAddress, aliceSNI[0], null, truststoreKeyStore),
                daleSslConfig,
                NioEventLoopGroup(1),
                NioEventLoopGroup(1),
                ConnectionConfiguration(),
            ).use { client ->
                client.start()
                val future = client.write(ByteArray(0))
                assertThatThrownBy {
                    future.get()
                }.isInstanceOf(ExecutionException::class.java)
                    .hasCauseInstanceOf(RuntimeException::class.java)
                    .hasStackTraceContaining("Connection was closed.")

                // Check HandshakeException is thrown and logged
                val expectedMessage = "Bad certificate identity or path. " +
                    "No subject alternative DNS name matching ${serverAddress.host} found"
                eventually {
                    loggingInterceptor.assertMessageExists(expectedMessage, Level.ERROR)
                }
            }
        }
    }

    @Test
    @Timeout(30)
    fun `tls handshake fails - requested SNI is not recognized`() {

        HttpServer(
            object : ListenerWithServer() {
                override fun onRequest(request: HttpRequest) {
                    server?.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), request.source)
                }
            },
            GatewayConfiguration(
                serverAddress.host,
                serverAddress.port,
                aliceSslConfig
            )
        ).use { server ->
            server.startAndWaitForStarted()
            HttpClient(
                DestinationInfo(serverAddress, bobSNI[0], null, truststoreKeyStore),
                chipSslConfig,
                NioEventLoopGroup(1),
                NioEventLoopGroup(1),
                ConnectionConfiguration(),
            ).use { client ->
                client.start()
                val future = client.write(ByteArray(0))
                assertThatThrownBy {
                    future.get()
                }.isInstanceOf(ExecutionException::class.java)
                    .hasCauseInstanceOf(RuntimeException::class.java)
                    .hasStackTraceContaining("Connection was closed.")
            }
        }

        eventually {
            loggingInterceptor.assertMessageExists(
                "Could not find a certificate matching the requested SNI value [hostname = ${bobSNI[0]}",
                Level.WARN
            )
        }
    }

    @Test
    @Timeout(30)
    fun `tls handshake fails - server presents revoked certificate`() {

        HttpServer(
            object : ListenerWithServer() {
                override fun onRequest(request: HttpRequest) {
                    server?.write(HttpResponseStatus.OK, serverResponseContent.toByteArray(Charsets.UTF_8), request.source)
                }
            },
            GatewayConfiguration(
                serverAddress.host,
                serverAddress.port,
                bobSslConfig
            )
        ).use { server ->
            server.startAndWaitForStarted()
            HttpClient(
                DestinationInfo(serverAddress, bobSNI[0], null, truststoreKeyStore),
                chipSslConfig,
                NioEventLoopGroup(1),
                NioEventLoopGroup(1),
                ConnectionConfiguration(),
            ).use { client ->
                client.start()
                val future = client.write(ByteArray(0))
                assertThatThrownBy {
                    future.get()
                }.isInstanceOf(ExecutionException::class.java)
                    .hasCauseInstanceOf(RuntimeException::class.java)
                    .hasStackTraceContaining("Connection was closed.")
            }
        }

        eventually {
            loggingInterceptor.assertMessageExists(
                "Bad certificate identity or path. PKIX path validation failed: " +
                    "java.security.cert.CertPathValidatorException: Certificate has been revoked",
                Level.ERROR
            )
        }
    }

    // Lightweight testing server which ignores SNI checks and presents invalid certificates
    // This server is not meant to receive requests.
    private class MitmServer(
        private val host: String,
        private val port: Int,
        private val sslConfig: SslConfiguration
    ) : Lifecycle {

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

        private class ServerChannelInitializer(parent: MitmServer) : ChannelInitializer<SocketChannel>() {

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
