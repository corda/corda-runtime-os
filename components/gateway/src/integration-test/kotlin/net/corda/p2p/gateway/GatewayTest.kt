package net.corda.p2p.gateway

import com.typesafe.config.ConfigFactory
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
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
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.gateway.messaging.http.ListenerWithServer
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import net.corda.p2p.schema.Schema.Companion.LINK_OUT_TOPIC
import net.corda.p2p.schema.Schema.Companion.SESSION_OUT_PARTITIONS
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterEach
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

class GatewayTest : TestBase() {
    companion object {
        private val logger = contextLogger()
    }

    private val sessionId = "session-1"

    private val nodeConfig = ConfigFactory.empty()

    private class Node(private val name: String) {
        private val topicService = TopicServiceImpl()
        val subscriptionFactory = InMemSubscriptionFactory(topicService)
        val publisherFactory = CordaPublisherFactory(topicService)
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
                nodeConfig = ConfigFactory.empty(),
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

    @Test
    @Timeout(30)
    fun `http client to gateway`() {
        alice.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1))))
        val serverAddress = URI.create("http://www.alice.net:10000")
        val linkInMessage = LinkInMessage(authenticatedP2PMessage(""))
        Gateway(
            createConfigurationServiceFor(GatewayConfiguration(serverAddress.host, serverAddress.port, aliceSslConfig),),
            alice.subscriptionFactory,
            alice.publisherFactory,
            lifecycleCoordinatorFactory,
            nodeConfig,
        ).use {
            it.startAndWaitForStarted()
            val serverInfo = DestinationInfo(serverAddress, aliceSNI[0], null)
            val responseReceived = CountDownLatch(1)
            val clientListener = object : HttpEventListener {
                override fun onMessage(message: HttpMessage) {
                    assertSoftly {
                        it.assertThat(message.source).isEqualTo(InetSocketAddress(serverAddress.host, serverAddress.port))
                        it.assertThat(message.statusCode).isEqualTo(HttpResponseStatus.OK)
                        it.assertThat(message.payload).isEmpty()
                    }
                    responseReceived.countDown()
                }
            }
            HttpClient(serverInfo, bobSslConfig, NioEventLoopGroup(1), NioEventLoopGroup(1), clientListener).use { client ->
                client.start()
                client.write(linkInMessage.toByteBuffer().array())
                responseReceived.await()
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

    @Test
    @Timeout(100)
    fun `gateway reconfiguration`() {
        val configurationCount = 3
        alice.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1))))
        val recipientServerUrl = URI.create("http://www.alice.net:10000")

        val linkInMessage = LinkInMessage(authenticatedP2PMessage(""))
        val linkOutMessage = LinkOutMessage.newBuilder().apply {
            header = LinkOutHeader("", NetworkType.CORDA_5, recipientServerUrl.toString())
            payload = authenticatedP2PMessage("link out")
        }.build()

        val configPublisher = ConfigPublisher()

        val messageReceivedLatch = CountDownLatch(1)
        val listenToOutboundMessages = object : ListenerWithServer() {
            override fun onOpen(event: HttpConnectionEvent) {
                assertThat(event.channel.localAddress()).isInstanceOfSatisfying(InetSocketAddress::class.java) {
                    assertThat(it.port).isEqualTo(recipientServerUrl.port)
                }
            }

            override fun onMessage(message: HttpMessage) {
                val p2pMessage = LinkInMessage.fromByteBuffer(ByteBuffer.wrap(message.payload))
                assertSoftly { softly ->
                    softly.assertThat(message.statusCode).isEqualTo(HttpResponseStatus.OK)
                    softly.assertThat(p2pMessage.payload).isInstanceOfSatisfying(AuthenticatedDataMessage::class.java) {
                        softly.assertThat(String(it.payload.array())).isEqualTo("link out")
                    }
                    server?.write(HttpResponseStatus.OK, ByteArray(0), message.source)
                    messageReceivedLatch.countDown()
                }
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
            ).use { gateway ->
                gateway.start()

                (1..configurationCount).map {
                    it + 20000
                }.map {
                    URI.create("http://www.alice.net:$it")
                }.forEach { url ->
                    val ackReceivedLatch = CountDownLatch(1)
                    val clientListener = object : HttpEventListener {
                        override fun onMessage(message: HttpMessage) {
                            assertSoftly {
                                it.assertThat(message.source).isEqualTo(InetSocketAddress(url.host, url.port))
                                it.assertThat(message.statusCode).isEqualTo(HttpResponseStatus.OK)
                                it.assertThat(message.payload).isEmpty()
                            }
                            ackReceivedLatch.countDown()
                        }
                    }

                    configPublisher.publishConfig(GatewayConfiguration(url.host, url.port, aliceSslConfig))
                    eventually(duration = 20.seconds) {
                        assertThat(gateway.isRunning).isTrue
                    }
                    eventually(duration = 10.seconds, waitBefore = Duration.ofMillis(200), waitBetween = Duration.ofMillis(200)) {
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
                        clientListener
                    ).use { secondInboundClient ->
                        secondInboundClient.start()

                        secondInboundClient.write(linkInMessage.toByteBuffer().array())
                        ackReceivedLatch.await()
                    }

                    alice.publish(Record(LINK_OUT_TOPIC, "key", linkOutMessage))
                    messageReceivedLatch.await()
                }
            }
        }
    }

    @Test
    @Timeout(60)
    fun `multiple clients to gateway`() {
        val clientNumber = 4
        val threadPool = NioEventLoopGroup(clientNumber)
        val serverAddress = URI.create("http://www.alice.net:10000")
        val clients = mutableListOf<HttpClient>()
        alice.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1))))
        Gateway(
            createConfigurationServiceFor(GatewayConfiguration(serverAddress.host, serverAddress.port, aliceSslConfig)),
            alice.subscriptionFactory,
            alice.publisherFactory,
            lifecycleCoordinatorFactory,
            nodeConfig,
        ).use {
            it.startAndWaitForStarted()
            val responseReceived = CountDownLatch(clientNumber)
            repeat(clientNumber) { index ->
                val serverInfo = DestinationInfo(serverAddress, aliceSNI[1], null)
                val clientListener = object : HttpEventListener {
                    override fun onMessage(message: HttpMessage) {
                        assertSoftly {
                            it.assertThat(message.source).isEqualTo(InetSocketAddress(serverAddress.host, serverAddress.port))
                            it.assertThat(message.statusCode).isEqualTo(HttpResponseStatus.OK)
                            it.assertThat(message.payload).isEmpty()
                        }
                        responseReceived.countDown()
                    }
                }
                val client = HttpClient(serverInfo, bobSslConfig, threadPool, threadPool, clientListener)
                client.start()
                val p2pOutMessage = LinkInMessage(authenticatedP2PMessage("Client-${index + 1}"))
                client.write(p2pOutMessage.toByteBuffer().array())
                clients.add(client)
            }
            responseReceived.await()
            clients.forEach { client -> client.stop() }
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

    @Test
    @Timeout(60)
    fun `gateway to multiple servers`() {
        val messageCount = 100
        val serversCount = 4

        // We first produce some messages which will be consumed by the Gateway.
        val deliveryLatch = CountDownLatch(serversCount * messageCount)
        val servers = (1..serversCount).map {
            it + 10000
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
                override fun onMessage(message: HttpMessage) {
                    val p2pMessage = LinkInMessage.fromByteBuffer(ByteBuffer.wrap(message.payload))
                    assertThat(
                        String((p2pMessage.payload as AuthenticatedDataMessage).payload.array())
                    )
                        .isEqualTo("Target-$serverUri")
                    server?.write(HttpResponseStatus.OK, ByteArray(0), message.source)
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
        val gatewayAddress = Pair("localhost", 10000)
        Gateway(
            createConfigurationServiceFor(GatewayConfiguration(gatewayAddress.first, gatewayAddress.second, aliceSslConfig)),
            alice.subscriptionFactory,
            alice.publisherFactory,
            lifecycleCoordinatorFactory,
            nodeConfig,
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
            nodeConfig = ConfigFactory.empty(),
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
            nodeConfig = ConfigFactory.empty(),
            partitionAssignmentListener = null
        )
        aliceSubscription.start()

        val startTime = Instant.now().toEpochMilli()
        // Start the gateways and let them run until all messages have been processed
        val gateways = listOf(
            Gateway(
                createConfigurationServiceFor(GatewayConfiguration(aliceGatewayAddress.host, aliceGatewayAddress.port, chipSslConfig)),
                alice.subscriptionFactory,
                alice.publisherFactory,
                lifecycleCoordinatorFactory,
                nodeConfig,
            ),
            Gateway(
                createConfigurationServiceFor(GatewayConfiguration(bobGatewayAddress.host, bobGatewayAddress.port, daleSslConfig)),
                bob.subscriptionFactory,
                bob.publisherFactory,
                lifecycleCoordinatorFactory,
                nodeConfig,
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
        gateways.forEach {
            it.close()
        }
    }

    private fun authenticatedP2PMessage(content: String) = AuthenticatedDataMessage.newBuilder().apply {
        header = CommonHeader(MessageType.DATA, 0, sessionId, 1L, Instant.now().toEpochMilli())
        payload = ByteBuffer.wrap(content.toByteArray())
        authTag = ByteBuffer.wrap(ByteArray(0))
    }.build()

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
        ).use { gateway ->
            configPublisher.publishConfig(
                GatewayConfiguration(
                    host,
                    10001,
                    aliceSslConfig
                )
            )
            gateway.startAndWaitForStarted()
            assertThat(gateway.state).isEqualTo(DominoTile.State.Started)

            // -20 is invalid port, serer should fail
            configPublisher.publishConfig(
                GatewayConfiguration(
                    host,
                    -20,
                    aliceSslConfig
                )
            )
            eventually(duration = 20.seconds) {
                assertThat(gateway.state).isEqualTo(DominoTile.State.StoppedDueToError)
            }
            assertThrows<ConnectException> {
                Socket(host, 10001).close()
            }

            configPublisher.publishConfig(
                GatewayConfiguration(
                    host,
                    10002,
                    aliceSslConfig
                )
            )
            eventually(duration = 20.seconds) {
                assertThat(gateway.state).isEqualTo(DominoTile.State.Started)
            }
            assertDoesNotThrow {
                Socket(host, 10002).close()
            }

            configPublisher.publishBadConfig()
            eventually(duration = 20.seconds) {
                assertThat(gateway.state).isEqualTo(DominoTile.State.StoppedDueToError)
            }
            assertThrows<ConnectException> {
                Socket(host, 10002).close()
            }
        }
    }
}
