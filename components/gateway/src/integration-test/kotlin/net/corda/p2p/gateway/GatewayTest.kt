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
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import net.corda.p2p.schema.Schema.Companion.LINK_OUT_TOPIC
import net.corda.p2p.schema.Schema.Companion.SESSION_OUT_PARTITIONS
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer
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
        val linkInMessage = LinkInMessage(authenticatedP2PMessage(String()))
        Gateway(
            GatewayConfiguration(serverAddress.host, serverAddress.port, aliceSslConfig),
            alice.subscriptionFactory,
            alice.publisherFactory
        ).use {
            it.start()
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
    @Timeout(60)
    fun `multiple clients to gateway`() {
        val clientNumber = 4
        val threadPool = NioEventLoopGroup(clientNumber)
        val serverAddress = URI.create("http://www.alice.net:10000")
        val clients = mutableListOf<HttpClient>()
        alice.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1))))
        Gateway(
            GatewayConfiguration(serverAddress.host, serverAddress.port, aliceSslConfig),
            alice.subscriptionFactory,
            alice.publisherFactory
        ).use {
            it.start()
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
        val messageCount = 10000
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
            HttpServer(serverUri.host, serverUri.port, chipSslConfig).also {
                it.addListener(object : HttpEventListener {
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
            it.start()
        }

        var startTime: Long
        var endTime: Long
        val gatewayAddress = Pair("localhost", 10000)
        Gateway(
            GatewayConfiguration(gatewayAddress.first, gatewayAddress.second, aliceSslConfig),
            alice.subscriptionFactory,
            alice.publisherFactory
        ).use {
            startTime = Instant.now().toEpochMilli()
            it.start()
            // Wait until all messages have been delivered
            deliveryLatch.await(1, TimeUnit.MINUTES)
            endTime = Instant.now().toEpochMilli()
        }

        logger.info("Done sending ${messageCount * serversCount} messages in ${endTime - startTime} milliseconds")
        servers.forEach { it.stop() }
    }

    @Test
    @Timeout(60)
    fun `gateway to gateway - dual stream`() {
        val aliceGatewayAddress = URI.create("http://www.chip.net:10003")
        val bobGatewayAddress = URI.create("http://www.dale.net:10004")
        val messageCount = 10000
        alice.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1))))
        bob.publish(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1))))
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
        val barrier = CountDownLatch(1)
        // Start the gateways
        val t1 = thread {
            Gateway(
                GatewayConfiguration(aliceGatewayAddress.host, aliceGatewayAddress.port, chipSslConfig),
                alice.subscriptionFactory,
                alice.publisherFactory
            ).also {
                it.start()
            }
                .use {
                    barrier.await()
                }
        }
        val t2 = thread {
            Gateway(
                GatewayConfiguration(bobGatewayAddress.host, bobGatewayAddress.port, daleSslConfig),
                bob.subscriptionFactory,
                bob.publisherFactory
            ).also {
                it.start()
            }.use {
                barrier.await()
            }
        }

        receivedLatch.await()
        barrier.countDown()
        val endTime = Instant.now().toEpochMilli()
        logger.info("Done processing ${messageCount * 2} in ${endTime - startTime} milliseconds.")

        t1.join()
        t2.join()
    }

    private fun authenticatedP2PMessage(content: String) = AuthenticatedDataMessage.newBuilder().apply {
        header = CommonHeader(MessageType.DATA, 0, sessionId, 1L, Instant.now().toEpochMilli())
        payload = ByteBuffer.wrap(content.toByteArray())
        authTag = ByteBuffer.wrap(ByteArray(0))
    }.build()
}
