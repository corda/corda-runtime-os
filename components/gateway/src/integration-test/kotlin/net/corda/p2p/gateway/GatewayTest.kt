package net.corda.p2p.gateway

import com.typesafe.config.ConfigFactory
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.http.HttpResponseStatus
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
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import net.corda.p2p.schema.Schema.Companion.LINK_OUT_TOPIC
import net.corda.p2p.schema.Schema.Companion.SESSION_OUT_PARTITIONS
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertDoesNotThrow
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class GatewayTest : TestBase() {
    companion object {
        private val logger = contextLogger()
    }

    private val sessionId = "session-1"

    private class Node(private val name: String) {
        private val topicService = TopicServiceImpl()
        val subscriptionFactory = InMemSubscriptionFactory(topicService)
        val publisherFactory = CordaPublisherFactory(topicService)

        fun publish(vararg records: Record<Any, Any>) {
            publisherFactory.createPublisher(PublisherConfig("$name.id")).use {
                it.publish(records.toList())
            }
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
        ).use {
            it.startAndWaitForStarted()
            val serverInfo = DestinationInfo(serverAddress, aliceSNI[0], null)
            HttpClient(serverInfo, bobSslConfig, NioEventLoopGroup(1), NioEventLoopGroup(1)).use { client ->
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
                client.addListener(clientListener)
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
    @Timeout(30)
    fun `gateway reconfiguration`() {
        val configurationCount = 3
        // TODO: YIFT
        // WIP: Need to verify what happpens with good configuration -> bad configuration (invalid port, invalid message all together) -> Different good configuration
        // Also test the outbound
        // Good -> Good
        // Bad->Bad->good
        // Good->good->good
        // Good -> bad -> bad -> good
        alice.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1))))
        val outboundServerUrl = URI.create("http://www.alice.net:10000")

        val linkInMessage = LinkInMessage(authenticatedP2PMessage(""))
        val linkOutMessage = LinkOutMessage.newBuilder().apply {
            header = LinkOutHeader("", NetworkType.CORDA_5, outboundServerUrl.toString())
            payload = authenticatedP2PMessage("link out")
        }.build()

        val configPublisher = ConfigPublisher()

        val outboundServerListeners = mutableSetOf<HttpEventListener>()
        HttpServer(
            outboundServerListeners,
            GatewayConfiguration(
                outboundServerUrl.host,
                outboundServerUrl.port,
                aliceSslConfig,
            )
        ).use { outboundServer ->
            outboundServer.startAndWaitForStarted()
            Gateway(
                configPublisher.readerService,
                alice.subscriptionFactory,
                alice.publisherFactory,
                lifecycleCoordinatorFactory,
            ).use { gateway ->
                gateway.start()

                (1..configurationCount).map {
                    it + 20000
                }.map {
                    URI.create("http://www.alice.net:$it")
                }.forEach { url ->
                    val outboundCountdownLatch = CountDownLatch(1)
                    val listenToOutboundMessages = object : HttpEventListener {
                        override fun onOpen(event: HttpConnectionEvent) {
                            assertThat(event.channel.localAddress()).isInstanceOfSatisfying(InetSocketAddress::class.java) {
                                assertThat(it.port).isEqualTo(outboundServerUrl.port)
                            }
                        }

                        override fun onMessage(message: HttpMessage) {
                            val p2pMessage = LinkInMessage.fromByteBuffer(ByteBuffer.wrap(message.payload))
                            assertSoftly { softly ->
                                softly.assertThat(message.statusCode).isEqualTo(HttpResponseStatus.OK)
                                softly.assertThat(p2pMessage.payload).isInstanceOfSatisfying(AuthenticatedDataMessage::class.java) {
                                    softly.assertThat(String(it.payload.array())).isEqualTo("link out")
                                }
                                outboundServer.write(HttpResponseStatus.OK, ByteArray(0), message.source)
                                outboundCountdownLatch.countDown()
                            }
                        }
                    }
                    outboundServerListeners.add(listenToOutboundMessages)
                    val inboundCountdownLatch = CountDownLatch(1)
                    val clientListener = object : HttpEventListener {
                        override fun onMessage(message: HttpMessage) {
                            assertSoftly {
                                it.assertThat(message.source).isEqualTo(InetSocketAddress(url.host, url.port))
                                it.assertThat(message.statusCode).isEqualTo(HttpResponseStatus.OK)
                                it.assertThat(message.payload).isEmpty()
                            }
                            inboundCountdownLatch.countDown()
                        }
                    }

                    configPublisher.publishConfig(GatewayConfiguration(url.host, url.port, aliceSslConfig))
                    gateway.startAndWaitForStarted()
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
                        NioEventLoopGroup(1)
                    ).use { secondInboundClient ->
                        secondInboundClient.addListener(clientListener)
                        secondInboundClient.start()

                        secondInboundClient.write(linkInMessage.toByteBuffer().array())
                        inboundCountdownLatch.await()
                    }

                    alice.publish(Record(LINK_OUT_TOPIC, "key", linkOutMessage))
                    outboundCountdownLatch.await()
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
        ).use {
            it.startAndWaitForStarted()
            val responseReceived = CountDownLatch(clientNumber)
            repeat(clientNumber) { index ->
                val serverInfo = DestinationInfo(serverAddress, aliceSNI[1], null)
                val client = HttpClient(serverInfo, bobSslConfig, threadPool, threadPool)
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
                client.addListener(clientListener)
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
            val listeners = mutableListOf<HttpEventListener>()
            HttpServer(
                listeners,
                GatewayConfiguration(serverUri.host, serverUri.port, chipSslConfig)
            ).also {
                listeners.add(object : HttpEventListener {
                    override fun onMessage(message: HttpMessage) {
                        val p2pMessage = LinkInMessage.fromByteBuffer(ByteBuffer.wrap(message.payload))
                        assertThat(
                            String((p2pMessage.payload as AuthenticatedDataMessage).payload.array())
                        )
                            .isEqualTo("Target-$serverUri")
                        it.write(HttpResponseStatus.OK, ByteArray(0), message.source)
                        deliveryLatch.countDown()
                    }
                })
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
        val aliceGatewayAddress = URI.create("http://www.chip.net:10003")
        val bobGatewayAddress = URI.create("http://www.dale.net:10004")
        val messageCount = 100
        alice.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1))))
        bob.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1))))

        val receivedLatch = CountDownLatch(messageCount * 2)
        val bobSubscription = bob.subscriptionFactory.createEventLogSubscription(
            subscriptionConfig = SubscriptionConfig("bob.intest", LINK_IN_TOPIC),
            processor = object : EventLogProcessor<Any, Any> {
                override fun onNext(events: List<EventLogRecord<Any, Any>>): List<Record<*, *>> {
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
            subscriptionConfig = SubscriptionConfig("bob.intest", LINK_IN_TOPIC),
            processor = object : EventLogProcessor<Any, Any> {
                override fun onNext(events: List<EventLogRecord<Any, Any>>): List<Record<*, *>> {
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
                lifecycleCoordinatorFactory
            ),
            Gateway(
                createConfigurationServiceFor(GatewayConfiguration(bobGatewayAddress.host, bobGatewayAddress.port, daleSslConfig)),
                bob.subscriptionFactory,
                bob.publisherFactory,
                lifecycleCoordinatorFactory
            )
        ).onEach {
            it.startAndWaitForStarted()
        }

        // Produce messages for each Gateway
        repeat(messageCount) {
            var msg = LinkOutMessage.newBuilder().apply {
                header = LinkOutHeader("", NetworkType.CORDA_5, bobGatewayAddress.toString())
                payload = authenticatedP2PMessage("Target-$bobGatewayAddress")
            }.build()
            alice.publish(Record(LINK_OUT_TOPIC, "key", msg))

            msg = LinkOutMessage.newBuilder().apply {
                header = LinkOutHeader("", NetworkType.CORDA_5, aliceGatewayAddress.toString())
                payload = authenticatedP2PMessage("Target-$aliceGatewayAddress")
            }.build()
            bob.publish(Record(LINK_OUT_TOPIC, "key", msg))
        }

        val threads = gateways.map {
            thread {
                receivedLatch.await()
                it.close()
            }
        }

        receivedLatch.await()
        val endTime = Instant.now().toEpochMilli()
        logger.info("Done processing ${messageCount * 2} in ${endTime - startTime} milliseconds.")

        threads.forEach {
            it.join()
        }
    }

    private fun authenticatedP2PMessage(content: String) = AuthenticatedDataMessage.newBuilder().apply {
        header = CommonHeader(MessageType.DATA, 0, sessionId, 1L, Instant.now().toEpochMilli())
        payload = ByteBuffer.wrap(content.toByteArray())
        authTag = ByteBuffer.wrap(ByteArray(0))
    }.build()
}
