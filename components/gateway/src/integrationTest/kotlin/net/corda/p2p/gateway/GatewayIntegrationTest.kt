package net.corda.p2p.gateway

import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.data.p2p.gateway.GatewayMessage
import net.corda.data.p2p.gateway.GatewayResponse
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.domino.logic.DependenciesVerifier
import net.corda.lifecycle.domino.logic.DominoTile
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
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.SessionPartitions
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.MessageType
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpConnectionEvent
import net.corda.p2p.gateway.messaging.http.HttpRequest
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.gateway.messaging.http.ListenerWithServer
import net.corda.schema.Schemas.P2P.Companion.LINK_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.LINK_OUT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.SESSION_OUT_PARTITIONS
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
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
import java.time.Duration
import java.time.Instant
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
    }

    private val sessionId = "session-1"
    private val instanceId = AtomicInteger(0)

    private val nodeConfig = SmartConfigImpl.empty()

    private class Node(private val name: String) {
        private val topicService = TopicServiceImpl()
        private val rpcTopicService = RPCTopicServiceImpl()
        private val lifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
        val subscriptionFactory = InMemSubscriptionFactory(topicService, rpcTopicService, lifecycleCoordinatorFactory)
        val publisherFactory = CordaPublisherFactory(topicService, rpcTopicService, lifecycleCoordinatorFactory)
        val publisher = publisherFactory.createPublisher(PublisherConfig("$name.id"))

        fun stop() {
            publisher.close()
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
            val serverAddress = URI.create("http://www.alice.net:10000")
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
                lifecycleCoordinatorFactory,
                nodeConfig,
                instanceId.incrementAndGet(),
            ).use {
                it.startAndWaitForStarted()
                val serverInfo = DestinationInfo(serverAddress, aliceSNI[0], null)
                HttpClient(
                    serverInfo,
                    bobSslConfig,
                    NioEventLoopGroup(1),
                    NioEventLoopGroup(1),
                    2.seconds
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
            val recipientServerUrl = URI.create("http://www.alice.net:10001")

            val linkInMessage = LinkInMessage(authenticatedP2PMessage(""))
            val linkOutMessage = LinkOutMessage.newBuilder().apply {
                header = LinkOutHeader("", NetworkType.CORDA_5, recipientServerUrl.toString())
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
                )
            ).use { recipientServer ->
                listenToOutboundMessages.server = recipientServer
                recipientServer.startAndWaitForStarted()
                Gateway(
                    configPublisher.readerService,
                    alice.subscriptionFactory,
                    alice.publisherFactory,
                    lifecycleCoordinatorFactory,
                    nodeConfig,
                    instanceId.incrementAndGet(),
                ).use { gateway ->
                    gateway.start()

                    (1..configurationCount).map {
                        it + 20000
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
                                null
                            ),
                            aliceSslConfig,
                            NioEventLoopGroup(1),
                            NioEventLoopGroup(1),
                            2.seconds
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
            val serverAddress = URI.create("http://www.alice.net:10002")
            alice.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1))))
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
                lifecycleCoordinatorFactory,
                nodeConfig,
                instanceId.incrementAndGet(),
            ).use {
                it.startAndWaitForStarted()
                (1..clientNumber).map { index ->
                    val serverInfo = DestinationInfo(serverAddress, aliceSNI[1], null)
                    val client = HttpClient(serverInfo, bobSslConfig, threadPool, threadPool, 2.seconds)
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

            // We first produce some messages which will be consumed by the Gateway.
            val deliveryLatch = CountDownLatch(serversCount * messageCount)
            val servers = (1..serversCount).map {
                it + 20000
            }.map {
                "http://www.chip.net:$it"
            }.onEach { serverUrl ->
                repeat(messageCount) {
                    val msg = LinkOutMessage.newBuilder().apply {
                        header = LinkOutHeader("", NetworkType.CORDA_5, serverUrl)
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
                    GatewayConfiguration(serverUri.host, serverUri.port, chipSslConfig)
                ).also {
                    serverListener.server = it
                }
            }.onEach {
                it.startAndWaitForStarted()
            }

            var startTime: Long
            var endTime: Long
            val gatewayAddress = Pair("localhost", 20000)
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
                lifecycleCoordinatorFactory,
                nodeConfig,
                instanceId.incrementAndGet(),
            ).use {
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
            val aliceGatewayAddress = URI.create("http://www.chip.net:11003")
            val bobGatewayAddress = URI.create("http://www.dale.net:11004")
            val messageCount = 100
            alice.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1)))).forEach { it.get() }
            bob.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1)))).forEach { it.get() }

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
            val lcf1 = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
            val lcf2 = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
            val gateways = listOf(
                Gateway(
                    createConfigurationServiceFor(
                        GatewayConfiguration(
                            aliceGatewayAddress.host,
                            aliceGatewayAddress.port,
                            chipSslConfig
                        ), lcf1
                    ),
                    alice.subscriptionFactory,
                    alice.publisherFactory,
                    lcf1,
                    nodeConfig,
                    instanceId.incrementAndGet(),
                ),
                Gateway(
                    createConfigurationServiceFor(
                        GatewayConfiguration(
                            bobGatewayAddress.host,
                            bobGatewayAddress.port,
                            daleSslConfig
                        ), lcf2
                    ),
                    bob.subscriptionFactory,
                    bob.publisherFactory,
                    lcf2,
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
                    header = LinkOutHeader("", NetworkType.CORDA_5, address)
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
                lifecycleCoordinatorFactory,
                nodeConfig,
                instanceId.incrementAndGet(),
            ).use { gateway ->
                logger.info("Publishing good config")
                configPublisher.publishConfig(
                    GatewayConfiguration(
                        host,
                        10005,
                        aliceSslConfig
                    )
                )
                gateway.startAndWaitForStarted()
                assertThat(gateway.dominoTile.state).isEqualTo(DominoTile.State.Started)

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
                    assertThat(gateway.dominoTile.state).isEqualTo(DominoTile.State.StoppedDueToChildStopped)
                }
                assertThrows<ConnectException> {
                    Socket(host, 10005).close()
                }

                logger.info("Publishing good config again")
                configPublisher.publishConfig(
                    GatewayConfiguration(
                        host,
                        10006,
                        aliceSslConfig
                    )
                )
                eventually(duration = 20.seconds) {
                    assertThat(gateway.dominoTile.state).isEqualTo(DominoTile.State.Started)
                }
                assertDoesNotThrow {
                    Socket(host, 10006).close()
                }

                logger.info("Publishing bad config again")
                configPublisher.publishBadConfig()
                eventually(duration = 20.seconds) {
                    assertThat(gateway.dominoTile.state).isEqualTo(DominoTile.State.StoppedDueToChildStopped)
                }
                assertThrows<ConnectException> {
                    Socket(host, 10006).close()
                }

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
                lifecycleCoordinatorFactory,
                nodeConfig,
                instanceId.incrementAndGet(),
            )

            assertDoesNotThrow {
                DependenciesVerifier.verify(gateway.dominoTile)
            }
        }
    }


}
