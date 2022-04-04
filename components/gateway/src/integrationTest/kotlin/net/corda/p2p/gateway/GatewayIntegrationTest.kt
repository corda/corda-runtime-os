package net.corda.p2p.gateway

import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.crypto.test.certificates.generation.CertificateAuthority
import net.corda.crypto.test.certificates.generation.CertificateAuthorityFactory
import net.corda.crypto.test.certificates.generation.PrivateKeyWithCertificate
import net.corda.crypto.test.certificates.generation.toFactoryDefinitions
import net.corda.crypto.test.certificates.generation.toKeystore
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.gateway.GatewayMessage
import net.corda.data.p2p.gateway.GatewayResponse
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.domino.logic.DependenciesVerifier
import net.corda.lifecycle.domino.logic.DominoTileState
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.p2p.GatewayTruststore
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.SessionPartitions
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.MessageType
import net.corda.p2p.gateway.messaging.ConnectionConfiguration
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpConnectionEvent
import net.corda.p2p.gateway.messaging.http.HttpRequest
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.gateway.messaging.http.KeyStoreWithPassword
import net.corda.p2p.gateway.messaging.http.ListenerWithServer
import net.corda.schema.Schemas
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_TRUSTSTORES
import net.corda.schema.Schemas.P2P.Companion.LINK_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.LINK_OUT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.SESSION_OUT_PARTITIONS
import net.corda.schema.TestSchema
import net.corda.test.util.eventually
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_SHA256_TEMPLATE
import net.corda.v5.cipher.suite.schemes.RSA_SHA256_TEMPLATE
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.PrincipalUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class GatewayIntegrationTest : TestBase() {
    companion object {
        private val logger = contextLogger()
        const val GROUP_ID = "Group - 1"
    }

    private val sessionId = "session-1"
    private val instanceId = AtomicInteger(0)

    private val nodeConfig = SmartConfigImpl.empty()

    private inner class Node(private val name: String) {
        private val topicService = TopicServiceImpl()
        private val rpcTopicService = RPCTopicServiceImpl()
        val lifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
        val subscriptionFactory = InMemSubscriptionFactory(topicService, rpcTopicService, lifecycleCoordinatorFactory)
        val publisherFactory = CordaPublisherFactory(topicService, rpcTopicService, lifecycleCoordinatorFactory)
        val publisher = publisherFactory.createPublisher(PublisherConfig("$name.id"))

        fun stop() {
            publisher.close()
        }

        fun publishTrustStore() {
            publish(
                Record(GATEWAY_TLS_TRUSTSTORES, GROUP_ID, GatewayTruststore(listOf(truststoreCertificatePem)))
            )
        }

        fun publish(vararg records: Record<Any, Any>): List<CompletableFuture<Unit>> {
            return publisher.publish(records.toList())
        }

        fun getRecords(topic: String, size: Int): Collection<EventLogRecord<Any, Any>> {
            val stop = CountDownLatch(size)
            val records = ConcurrentHashMap.newKeySet<EventLogRecord<Any, Any>>()
            subscriptionFactory.createEventLogSubscription(
                subscriptionConfig = SubscriptionConfig("group.$name", topic),
                processor = object : EventLogProcessor<Any, Any> {
                    override fun onNext(events: List<EventLogRecord<Any, Any>>): List<Record<*, *>> {
                        events.forEach {
                            records.add(it)
                            stop.countDown()
                        }

                        return emptyList()
                    }

                    override val keyClass = Any::class.java
                    override val valueClass = Any::class.java
                },
                nodeConfig = SmartConfigImpl.empty(),
                partitionAssignmentListener = null
            ).use {
                it.start()
                stop.await()
            }
            return records
        }
    }

    private val alice = Node("alice")
    private val bob = Node("bob")

    @AfterEach
    fun setup() {
        alice.stop()
        bob.stop()
    }

    @Nested
    inner class ClientToGatewayTests {
        @Test
        @Timeout(30)
        fun `http client to gateway`() {
            alice.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1))))
            val port = getOpenPort()
            val serverAddress = URI.create("http://www.alice.net:$port")
            val linkInMessage = LinkInMessage(authenticatedP2PMessage(""))
            val gatewayMessage = GatewayMessage("msg-id", linkInMessage.payload)
            Gateway(
                createConfigurationServiceFor(
                    GatewayConfiguration(
                        serverAddress.host,
                        serverAddress.port,
                        aliceSslConfig
                    ),
                ),
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                nodeConfig,
                instanceId.incrementAndGet(),
            ).use {
                publishKeyStoreCertificatesAndKeys(alice.publisher, aliceKeyStore)
                it.startAndWaitForStarted()
                val serverInfo = DestinationInfo(serverAddress, aliceSNI[0], null, truststoreKeyStore)
                HttpClient(
                    serverInfo,
                    bobSslConfig,
                    NioEventLoopGroup(1),
                    NioEventLoopGroup(1),
                    ConnectionConfiguration(),
                ).use { client ->
                    client.start()
                    val httpResponse = client.write(gatewayMessage.toByteBuffer().array()).get()
                    assertThat(httpResponse.statusCode).isEqualTo(HttpResponseStatus.OK)
                    assertThat(httpResponse.payload).isNotNull
                    val gatewayResponse = GatewayResponse.fromByteBuffer(ByteBuffer.wrap(httpResponse.payload))
                    assertThat(gatewayResponse.id).isEqualTo(gatewayMessage.id)
                }
            }

            // Verify Gateway has successfully forwarded the message to the P2P_IN topic
            val publishedRecords = alice.getRecords(LINK_IN_TOPIC, 1)
            assertThat(publishedRecords)
                .hasSize(1).allSatisfy {
                    assertThat(it.value).isInstanceOfSatisfying(LinkInMessage::class.java) {
                        assertThat(it.payload).isInstanceOfSatisfying(AuthenticatedDataMessage::class.java) {
                            assertThat(it).isEqualTo(linkInMessage.payload)
                        }
                    }
                }
        }
    }

    @Nested
    inner class ReconfigurationTests {
        @Test
        @Timeout(100)
        fun `gateway reconfiguration`() {
            val configurationCount = 3
            alice.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1))))
            alice.publishTrustStore()
            val recipientServerUrl = URI.create("http://www.alice.net:${getOpenPort()}")

            val linkInMessage = LinkInMessage(authenticatedP2PMessage(""))
            val linkOutMessage = LinkOutMessage.newBuilder().apply {
                header = LinkOutHeader(
                    HoldingIdentity("", GROUP_ID),
                    NetworkType.CORDA_5,
                    recipientServerUrl.toString(),
                )
                payload = authenticatedP2PMessage("link out")
            }.build()
            val gatewayMessage = GatewayMessage("msg-id", linkInMessage.payload)

            val configPublisher = ConfigPublisher()

            val messageReceivedLatch = AtomicReference(CountDownLatch(1))
            val listenToOutboundMessages = object : ListenerWithServer() {
                override fun onOpen(event: HttpConnectionEvent) {
                    assertThat(event.channel.localAddress()).isInstanceOfSatisfying(InetSocketAddress::class.java) {
                        assertThat(it.port).isEqualTo(recipientServerUrl.port)
                    }
                }

                override fun onRequest(request: HttpRequest) {
                    val receivedGatewayMessage = GatewayMessage.fromByteBuffer(ByteBuffer.wrap(request.payload))
                    val p2pMessage = LinkInMessage(receivedGatewayMessage.payload)
                    assertThat(p2pMessage.payload).isInstanceOfSatisfying(AuthenticatedDataMessage::class.java) {
                        assertThat(String(it.payload.array())).isEqualTo("link out")
                    }
                    server?.write(HttpResponseStatus.OK, ByteArray(0), request.source)
                    messageReceivedLatch.get().countDown()
                }
            }
            HttpServer(
                listenToOutboundMessages,
                GatewayConfiguration(
                    recipientServerUrl.host,
                    recipientServerUrl.port,
                    aliceSslConfig,
                ),
                aliceKeyStore,
            ).use { recipientServer ->
                publishKeyStoreCertificatesAndKeys(alice.publisher, aliceKeyStore)
                listenToOutboundMessages.server = recipientServer
                recipientServer.startAndWaitForStarted()
                Gateway(
                    configPublisher.readerService,
                    alice.subscriptionFactory,
                    alice.publisherFactory,
                    alice.lifecycleCoordinatorFactory,
                    nodeConfig,
                    instanceId.incrementAndGet(),
                ).use { gateway ->
                    gateway.start()

                    (1..configurationCount).map {
                        getOpenPort()
                    }.map {
                        URI.create("http://www.alice.net:$it")
                    }.forEach { url ->
                        configPublisher.publishConfig(GatewayConfiguration(url.host, url.port, aliceSslConfig))
                        eventually(duration = 20.seconds) {
                            assertThat(gateway.isRunning).isTrue
                        }
                        eventually(
                            duration = 10.seconds,
                            waitBefore = Duration.ofMillis(200),
                            waitBetween = Duration.ofMillis(200)
                        ) {
                            assertDoesNotThrow {
                                Socket(url.host, url.port).close()
                            }
                        }

                        HttpClient(
                            DestinationInfo(
                                url,
                                aliceSNI[0],
                                null,
                                truststoreKeyStore
                            ),
                            aliceSslConfig,
                            NioEventLoopGroup(1),
                            NioEventLoopGroup(1),
                            ConnectionConfiguration(),
                        ).use { secondInboundClient ->
                            secondInboundClient.start()

                            val httpResponse = secondInboundClient.write(gatewayMessage.toByteBuffer().array()).get()
                            assertThat(httpResponse.statusCode).isEqualTo(HttpResponseStatus.OK)
                            assertThat(httpResponse.payload).isNotNull
                            val gatewayResponse = GatewayResponse.fromByteBuffer(ByteBuffer.wrap(httpResponse.payload))
                            assertThat(gatewayResponse.id).isEqualTo(gatewayMessage.id)
                        }

                        messageReceivedLatch.set(CountDownLatch(1))
                        alice.publish(Record(LINK_OUT_TOPIC, "key", linkOutMessage))
                        messageReceivedLatch.get().await()
                    }
                }
            }
        }
    }

    @Nested
    inner class MultipleClientsToGatewayTests {
        @Test
        @Timeout(60)
        fun `multiple clients to gateway`() {
            val msgNumber = AtomicInteger(1)
            val clientNumber = 4
            val threadPool = NioEventLoopGroup(clientNumber)
            val serverAddress = URI.create("http://www.alice.net:${getOpenPort()}")
            alice.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1))))
            publishKeyStoreCertificatesAndKeys(alice.publisher, aliceKeyStore)
            Gateway(
                createConfigurationServiceFor(
                    GatewayConfiguration(
                        serverAddress.host,
                        serverAddress.port,
                        aliceSslConfig
                    )
                ),
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                nodeConfig,
                instanceId.incrementAndGet(),
            ).use {
                it.startAndWaitForStarted()
                (1..clientNumber).map { index ->
                    val serverInfo = DestinationInfo(serverAddress, aliceSNI[1], null, truststoreKeyStore)
                    val client = HttpClient(serverInfo, bobSslConfig, threadPool, threadPool, ConnectionConfiguration())
                    client.start()
                    val p2pOutMessage = LinkInMessage(authenticatedP2PMessage("Client-$index"))
                    val gatewayMessage = GatewayMessage("msg-${msgNumber.getAndIncrement()}", p2pOutMessage.payload)
                    val future = client.write(gatewayMessage.toByteBuffer().array())
                    Triple(client, gatewayMessage, future)
                }.forEach { (client, gatewayMessage, future) ->
                    val httpResponse = future.get()
                    assertThat(httpResponse.statusCode).isEqualTo(HttpResponseStatus.OK)
                    assertThat(httpResponse.payload).isNotNull
                    val gatewayResponse = GatewayResponse.fromByteBuffer(ByteBuffer.wrap(httpResponse.payload))
                    assertThat(gatewayResponse.id).isEqualTo(gatewayMessage.id)
                    client.stop()
                }
            }

            // Verify Gateway has received all [clientNumber] messages and that they were forwarded to the P2P_IN topic
            val publishedRecords = alice.getRecords(LINK_IN_TOPIC, clientNumber)
                .asSequence()
                .map { it.value }
                .filterIsInstance<LinkInMessage>()
                .map { it.payload }
                .filterIsInstance<AuthenticatedDataMessage>()
                .map { it.payload.array() }
                .map { String(it) }
                .map { it.substringAfter("Client-") }
                .map { it.toInt() }
                .toList()
            assertThat(publishedRecords).hasSize(clientNumber).contains(1, 2, 3, 4)
        }
    }

    @Nested
    inner class GatewayToMultipleServersTest {
        @Test
        @Timeout(60)
        fun `gateway to multiple servers`() {
            val messageCount = 100
            val serversCount = 4
            alice.publishTrustStore()

            // We first produce some messages which will be consumed by the Gateway.
            val deliveryLatch = CountDownLatch(serversCount * messageCount)
            val servers = (1..serversCount).map {
                getOpenPort()
            }.map {
                "http://www.chip.net:$it"
            }.onEach { serverUrl ->
                repeat(messageCount) {
                    val msg = LinkOutMessage.newBuilder().apply {
                        header = LinkOutHeader(
                            HoldingIdentity("", GROUP_ID),
                            NetworkType.CORDA_5,
                            serverUrl,
                        )
                        payload = authenticatedP2PMessage("Target-$serverUrl")
                    }.build()
                    alice.publish(Record(LINK_OUT_TOPIC, "key", msg))
                }
            }.map { serverUrl ->
                URI.create(serverUrl)
            }.map { serverUri ->
                val serverListener = object : ListenerWithServer() {
                    override fun onRequest(request: HttpRequest) {
                        val gatewayMessage = GatewayMessage.fromByteBuffer(ByteBuffer.wrap(request.payload))
                        val p2pMessage = LinkInMessage(gatewayMessage.payload)
                        assertThat(
                            String((p2pMessage.payload as AuthenticatedDataMessage).payload.array())
                        )
                            .isEqualTo("Target-$serverUri")
                        val gatewayResponse = GatewayResponse(gatewayMessage.id)
                        server?.write(HttpResponseStatus.OK, gatewayResponse.toByteBuffer().array(), request.source)
                        deliveryLatch.countDown()
                    }
                }
                HttpServer(
                    serverListener,
                    GatewayConfiguration(serverUri.host, serverUri.port, chipSslConfig),
                    chipKeyStore,
                ).also {
                    serverListener.server = it
                }
            }.onEach {
                it.startAndWaitForStarted()
            }

            var startTime: Long
            var endTime: Long
            val gatewayAddress = Pair("localhost", getOpenPort())
            Gateway(
                createConfigurationServiceFor(
                    GatewayConfiguration(
                        gatewayAddress.first,
                        gatewayAddress.second,
                        aliceSslConfig
                    )
                ),
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                nodeConfig,
                instanceId.incrementAndGet(),
            ).use {
                publishKeyStoreCertificatesAndKeys(alice.publisher, aliceKeyStore)
                startTime = Instant.now().toEpochMilli()
                it.startAndWaitForStarted()
                // Wait until all messages have been delivered
                deliveryLatch.await(1, TimeUnit.MINUTES)
                endTime = Instant.now().toEpochMilli()
            }

            logger.info("Done sending ${messageCount * serversCount} messages in ${endTime - startTime} milliseconds")
            servers.forEach { it.close() }
        }
    }

    @Nested
    inner class DualStreamTests {
        @Test
        @Timeout(60)
        fun `gateway to gateway - dual stream`() {
            val aliceGatewayAddress = URI.create("http://www.chip.net:${getOpenPort()}")
            val bobGatewayAddress = URI.create("http://www.dale.net:${getOpenPort()}")
            val messageCount = 100
            alice.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1)))).forEach { it.get() }
            bob.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1)))).forEach { it.get() }
            alice.publishTrustStore()
            bob.publishTrustStore()
            publishKeyStoreCertificatesAndKeys(alice.publisher, chipKeyStore)
            publishKeyStoreCertificatesAndKeys(bob.publisher, daleKeyStore)

            val receivedLatch = CountDownLatch(messageCount * 2)
            var bobReceivedMessages = 0
            var aliceReceivedMessages = 0
            val bobSubscription = bob.subscriptionFactory.createEventLogSubscription(
                subscriptionConfig = SubscriptionConfig("bob.intest", LINK_IN_TOPIC),
                processor = object : EventLogProcessor<Any, Any> {
                    override fun onNext(events: List<EventLogRecord<Any, Any>>): List<Record<*, *>> {
                        bobReceivedMessages += events.size
                        repeat(events.size) {
                            receivedLatch.countDown()
                        }

                        return emptyList()
                    }

                    override val keyClass = Any::class.java
                    override val valueClass = Any::class.java
                },
                nodeConfig = SmartConfigImpl.empty(),
                partitionAssignmentListener = null
            )
            bobSubscription.start()
            val aliceSubscription = alice.subscriptionFactory.createEventLogSubscription(
                subscriptionConfig = SubscriptionConfig("alice.intest", LINK_IN_TOPIC),
                processor = object : EventLogProcessor<Any, Any> {
                    override fun onNext(events: List<EventLogRecord<Any, Any>>): List<Record<*, *>> {
                        aliceReceivedMessages += events.size
                        repeat(events.size) {
                            receivedLatch.countDown()
                        }

                        return emptyList()
                    }

                    override val keyClass = Any::class.java
                    override val valueClass = Any::class.java
                },
                nodeConfig = SmartConfigImpl.empty(),
                partitionAssignmentListener = null
            )
            aliceSubscription.start()

            val startTime = Instant.now().toEpochMilli()
            // Start the gateways and let them run until all messages have been processed
            val gateways = listOf(
                Gateway(
                    createConfigurationServiceFor(
                        GatewayConfiguration(
                            aliceGatewayAddress.host,
                            aliceGatewayAddress.port,
                            chipSslConfig
                        ),
                        alice.lifecycleCoordinatorFactory
                    ),
                    alice.subscriptionFactory,
                    alice.publisherFactory,
                    alice.lifecycleCoordinatorFactory,
                    nodeConfig,
                    instanceId.incrementAndGet(),
                ),
                Gateway(
                    createConfigurationServiceFor(
                        GatewayConfiguration(
                            bobGatewayAddress.host,
                            bobGatewayAddress.port,
                            daleSslConfig
                        ),
                        bob.lifecycleCoordinatorFactory
                    ),
                    bob.subscriptionFactory,
                    bob.publisherFactory,
                    bob.lifecycleCoordinatorFactory,
                    nodeConfig,
                    instanceId.incrementAndGet(),
                )
            ).onEach {
                it.startAndWaitForStarted()
            }

            // Produce messages for each Gateway
            (1..messageCount).flatMap {
                listOf(
                    bobGatewayAddress.toString() to alice,
                    aliceGatewayAddress.toString() to bob
                )
            }.flatMap { (address, node) ->
                val msg = LinkOutMessage.newBuilder().apply {
                    header = LinkOutHeader(
                        HoldingIdentity("", GROUP_ID),
                        NetworkType.CORDA_5,
                        address
                    )
                    payload = authenticatedP2PMessage("Target-$address")
                }.build()
                node.publish(Record(LINK_OUT_TOPIC, "key", msg))
            }.forEach {
                it.join()
            }

            val allMessagesDelivered = receivedLatch.await(30, TimeUnit.SECONDS)
            if (!allMessagesDelivered) {
                fail(
                    "Not all messages were delivered successfully. Bob received $bobReceivedMessages messages (expected $messageCount), " +
                        "Alice received $aliceReceivedMessages (expected $messageCount)"
                )
            }

            val endTime = Instant.now().toEpochMilli()
            logger.info("Done processing ${messageCount * 2} in ${endTime - startTime} milliseconds.")
            receivedLatch.await()
            gateways.map {
                thread {
                    it.close()
                }
            }.forEach {
                it.join()
            }
        }
    }

    private fun authenticatedP2PMessage(content: String) = AuthenticatedDataMessage.newBuilder().apply {
        header = CommonHeader(MessageType.DATA, 0, sessionId, 1L, Instant.now().toEpochMilli())
        payload = ByteBuffer.wrap(content.toByteArray())
        authTag = ByteBuffer.wrap(ByteArray(0))
    }.build()

    @Nested
    inner class BadConfigurationTests {
        @Test
        @Timeout(120)
        fun `Gateway can recover from bad configuration`() {
            val configPublisher = ConfigPublisher()
            val host = "www.alice.net"
            Gateway(
                configPublisher.readerService,
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                nodeConfig,
                instanceId.incrementAndGet(),
            ).use { gateway ->
                val port = getOpenPort()
                logger.info("Publishing good config")
                configPublisher.publishConfig(
                    GatewayConfiguration(
                        host,
                        port,
                        aliceSslConfig
                    )
                )
                gateway.startAndWaitForStarted()
                assertThat(gateway.dominoTile.state).isEqualTo(DominoTileState.Started)

                logger.info("Publishing bad config")
                // -20 is invalid port, serer should fail
                configPublisher.publishConfig(
                    GatewayConfiguration(
                        host,
                        -20,
                        aliceSslConfig
                    )
                )
                eventually(duration = 20.seconds) {
                    assertThat(gateway.dominoTile.state).isEqualTo(DominoTileState.StoppedDueToChildStopped)
                }
                eventually(duration = 20.seconds) {
                    assertThrows<ConnectException> {
                        Socket(host, port).close()
                    }
                }

                logger.info("Publishing good config again")
                val anotherPort = getOpenPort()
                configPublisher.publishConfig(
                    GatewayConfiguration(
                        host,
                        anotherPort,
                        aliceSslConfig
                    )
                )
                eventually(duration = 20.seconds) {
                    assertThat(gateway.dominoTile.state).isEqualTo(DominoTileState.Started)
                }
                assertDoesNotThrow {
                    Socket(host, anotherPort).close()
                }

                logger.info("Publishing bad config again")
                configPublisher.publishBadConfig()
                eventually(duration = 20.seconds) {
                    assertThat(gateway.dominoTile.state).isEqualTo(DominoTileState.StoppedDueToChildStopped)
                }
                eventually(duration = 20.seconds) {
                    assertThrows<ConnectException> {
                        Socket(host, anotherPort).close()
                    }
                }
            }
        }
    }

    @Nested
    inner class DynamicKeyStore {
        private fun testClientWith(
            server: URI,
            trustStore: KeyStore,
        ) {
            val serverInfo = DestinationInfo(
                server, server.host, null,
                trustStore
            )
            val linkInMessage = LinkInMessage(authenticatedP2PMessage(""))
            val gatewayMessage = GatewayMessage(UUID.randomUUID().toString(), linkInMessage.payload)
            HttpClient(
                serverInfo,
                SslConfiguration(RevocationConfig(RevocationConfigMode.OFF)),
                NioEventLoopGroup(1),
                NioEventLoopGroup(1),
                ConnectionConfiguration(),
            ).use { client ->
                client.start()
                val httpResponse = client.write(gatewayMessage.toByteBuffer().array()).getOrThrow()
                assertThat(httpResponse.statusCode).isEqualTo(HttpResponseStatus.OK)
                assertThat(httpResponse.payload).isNotNull
                val gatewayResponse = GatewayResponse.fromByteBuffer(ByteBuffer.wrap(httpResponse.payload))
                assertThat(gatewayResponse.id).isEqualTo(gatewayMessage.id)
            }
        }

        private fun CertificateAuthority.toGatewayTrustStore(): GatewayTruststore {
            return GatewayTruststore(
                listOf(
                    this.caCertificate.toPem()
                )
            )
        }

        fun PrivateKeyWithCertificate.toKeyStoreAndPassword(): KeyStoreWithPassword {
            return KeyStoreWithPassword(
                this.toKeyStore(),
                CertificateAuthority.PASSWORD
            )
        }

        @Test
        @Timeout(120)
        fun `key store can change dynamically`() {
            val aliceAddress = URI.create("http://www.alice.net:${getOpenPort()}")
            val bobAddress = URI.create("http://www.bob.net:${getOpenPort()}")
            val server = Node("server")
            val configPublisher = ConfigPublisher()
            configPublisher.publishConfig(
                GatewayConfiguration(
                    aliceAddress.host,
                    aliceAddress.port,
                    aliceSslConfig
                ),
            )
            server.publish(
                Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1)))
            )
            Gateway(
                configPublisher.readerService,
                server.subscriptionFactory,
                server.publisherFactory,
                server.lifecycleCoordinatorFactory,
                nodeConfig,
                instanceId.incrementAndGet(),
            ).use { gateway ->
                gateway.startAndWaitForStarted()
                val firstCertificatesAuthority = CertificateAuthorityFactory
                    .createMemoryAuthority(RSA_SHA256_TEMPLATE.toFactoryDefinitions())
                // Client should fail without trust store certificates
                assertThrows<RuntimeException> {
                    testClientWith(aliceAddress, firstCertificatesAuthority.caCertificate.toKeystore())
                }

                // Publish the trust store
                server.publish(
                    Record(GATEWAY_TLS_TRUSTSTORES, GROUP_ID, firstCertificatesAuthority.toGatewayTrustStore()),
                )

                // Client should fail without any keys
                assertThrows<RuntimeException> {
                    testClientWith(aliceAddress, firstCertificatesAuthority.caCertificate.toKeystore())
                }

                // Publish the first key pair
                val keyStore = firstCertificatesAuthority.generateKeyAndCertificate(aliceAddress.host).toKeyStoreAndPassword()
                publishKeyStoreCertificatesAndKeys(server.publisher, keyStore)

                // Client should now pass
                testClientWith(aliceAddress, firstCertificatesAuthority.caCertificate.toKeystore())

                // Delete the first pair
                server.publish(
                    Record(
                        TestSchema.CRYPTO_KEYS_TOPIC,
                        keyStore.keyStore.aliases().nextElement(),
                        null
                    )
                )

                // Client should fail again...
                eventually {
                    assertThrows<RuntimeException> {
                        testClientWith(aliceAddress, firstCertificatesAuthority.caCertificate.toKeystore())
                    }
                }

                // publish the same pair again
                publishKeyStoreCertificatesAndKeys(server.publisher, keyStore)

                // Client should now pass
                testClientWith(aliceAddress, firstCertificatesAuthority.caCertificate.toKeystore())

                // Delete the certificates
                val subject = PrincipalUtil.getSubjectX509Principal(
                    keyStore.keyStore.getCertificate(keyStore.keyStore.aliases().nextElement()) as X509Certificate
                ).name
                server.publish(
                    Record(
                        Schemas.P2P.GATEWAY_TLS_CERTIFICATES,
                        subject,
                        null
                    )
                )
                eventually {
                    assertThrows<RuntimeException> {
                        testClientWith(aliceAddress, firstCertificatesAuthority.caCertificate.toKeystore())
                    }
                }

                // publish it again
                publishKeyStoreCertificatesAndKeys(server.publisher, keyStore)
                testClientWith(aliceAddress, firstCertificatesAuthority.caCertificate.toKeystore())

                // Change the host
                configPublisher.publishConfig(GatewayConfiguration(bobAddress.host, bobAddress.port, aliceSslConfig))
                val bobKeyStore = firstCertificatesAuthority.generateKeyAndCertificate(bobAddress.host).toKeyStoreAndPassword()
                publishKeyStoreCertificatesAndKeys(server.publisher, bobKeyStore)

                // Client should pass with new host
                testClientWith(bobAddress, firstCertificatesAuthority.caCertificate.toKeystore())

                // new trust store...
                val secondCertificatesAuthority = CertificateAuthorityFactory
                    .createMemoryAuthority(ECDSA_SECP256R1_SHA256_TEMPLATE.toFactoryDefinitions())
                server.publish(
                    Record(GATEWAY_TLS_TRUSTSTORES, GROUP_ID, secondCertificatesAuthority.toGatewayTrustStore()),
                )

                // replace the first pair
                val newKeyStore = secondCertificatesAuthority.generateKeyAndCertificate(bobAddress.host).toKeyStoreAndPassword()
                publishKeyStoreCertificatesAndKeys(server.publisher, newKeyStore)
                testClientWith(bobAddress, secondCertificatesAuthority.caCertificate.toKeystore())

                // verify that the old trust store will fail
                eventually {
                    assertThrows<RuntimeException> {
                        testClientWith(bobAddress, firstCertificatesAuthority.caCertificate.toKeystore())
                    }
                }

                // new trust store and pair...
                val thirdCertificatesAuthority = CertificateAuthorityFactory
                    .createMemoryAuthority(ECDSA_SECP256R1_SHA256_TEMPLATE.toFactoryDefinitions())
                server.publish(
                    Record(GATEWAY_TLS_TRUSTSTORES, GROUP_ID, thirdCertificatesAuthority.toGatewayTrustStore()),
                )

                // publish new pair with new alias
                val newerKeyStore = thirdCertificatesAuthority.generateKeyAndCertificate(bobAddress.host).toKeyStoreAndPassword()
                publishKeyStoreCertificatesAndKeys(server.publisher, newerKeyStore)
                testClientWith(bobAddress, thirdCertificatesAuthority.caCertificate.toKeystore())
            }
        }
    }

    @Nested
    inner class DominoLogicTests {
        @Test
        fun `domino logic dependencies are setup successfully for gateway`() {
            val configPublisher = ConfigPublisher()
            val gateway = Gateway(
                configPublisher.readerService,
                alice.subscriptionFactory,
                alice.publisherFactory,
                alice.lifecycleCoordinatorFactory,
                nodeConfig,
                instanceId.incrementAndGet(),
            )

            assertDoesNotThrow {
                DependenciesVerifier.verify(gateway.dominoTile)
            }
        }
    }
}
