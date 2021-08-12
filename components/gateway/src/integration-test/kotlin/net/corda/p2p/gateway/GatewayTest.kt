package net.corda.p2p.gateway

import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.SessionPartitions
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.MessageType
import net.corda.p2p.gateway.Gateway.Companion.CONSUMER_GROUP_ID
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import net.corda.p2p.schema.Schema.Companion.LINK_OUT_TOPIC
import net.corda.p2p.schema.Schema.Companion.SESSION_OUT_PARTITIONS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class GatewayTest : TestBase() {

    private var topicServiceAlice: TopicService? = null
    private var topicServiceBob: TopicService? = null
    private val sessionId = "session-1"

    @BeforeEach
    fun setup() {
        topicServiceAlice = TopicServiceStub()
        topicServiceBob = TopicServiceStub()
    }
    @AfterEach
    fun teardown() {
        topicServiceAlice = null
        topicServiceAlice = null
    }

    @Test
    @Timeout(30)
    fun `http client to gateway`() {
        topicServiceAlice!!.addRecords(listOf(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1)))))
        val serverAddress = URI.create("http://localhost:10000")
        val linkInMessage = LinkInMessage(authenticatedP2PMessage(String()))
        Gateway(GatewayConfiguration(serverAddress.host, serverAddress.port, aliceSslConfig),
                SubscriptionFactoryStub(topicServiceAlice!!),
                PublisherFactoryStub(topicServiceAlice!!)
        ).use {
            it.start()
            val serverInfo = DestinationInfo(serverAddress, aliceSNI[0], null)
            HttpClient(serverInfo, bobSslConfig, NioEventLoopGroup(1), NioEventLoopGroup(1)).use { client->
                val responseReceived = CountDownLatch(1)
                val clientListener = object : HttpEventListener {
                    override fun onMessage(message: HttpMessage) {
                        assertEquals(InetSocketAddress(serverAddress.host, serverAddress.port), message.source)
                        assertEquals(HttpResponseStatus.OK, message.statusCode)
                        assertTrue(message.payload.isEmpty())
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
        val publishedRecords = topicServiceAlice!!.getRecords(LINK_IN_TOPIC, CONSUMER_GROUP_ID, -1, false)
        assertEquals(1, publishedRecords.size)
        val receivedMessage = publishedRecords.first().record.value
        assertTrue(receivedMessage is LinkInMessage)
        val payload = (receivedMessage as LinkInMessage).payload as AuthenticatedDataMessage
        assertEquals(linkInMessage.payload, payload)
    }


    @Test
    @Timeout(60)
    fun `multiple clients to gateway`() {
        val clientNumber = 4
        val threadPool = NioEventLoopGroup(clientNumber)
        val serverAddress = URI.create("http://localhost:10000")
        val clients = mutableListOf<HttpClient>()
        topicServiceAlice!!.addRecords(listOf(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1)))))
        Gateway(GatewayConfiguration(serverAddress.host, serverAddress.port, aliceSslConfig),
            SubscriptionFactoryStub(topicServiceAlice!!),
            PublisherFactoryStub(topicServiceAlice!!)).use {
            it.start()
            val responseReceived = CountDownLatch(clientNumber)
            repeat(clientNumber) { index ->
                val serverInfo = DestinationInfo(serverAddress, aliceSNI[1], null)
                val client = HttpClient(serverInfo, bobSslConfig, threadPool, threadPool)
                val clientListener = object : HttpEventListener {
                    override fun onMessage(message: HttpMessage) {
                        assertEquals(InetSocketAddress(serverAddress.host, serverAddress.port), message.source)
                        assertEquals(HttpResponseStatus.OK, message.statusCode)
                        assertTrue(message.payload.isEmpty())
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
        val publishedRecords = topicServiceAlice!!.getRecords(LINK_IN_TOPIC, CONSUMER_GROUP_ID, -1, false)
        assertEquals(clientNumber, publishedRecords.size)
        var sum = 0
        // All clients should have sent a message containing their ID (index in the range). We verify that by simply adding them and comparing to the expected
        // value which is n(n+1)/2 where n is the number of clients
        publishedRecords.map { (it.record.value as LinkInMessage).payload as AuthenticatedDataMessage }.forEach {
            sum += String(it.payload.array()).substringAfter("Client-") .toInt()
        }
        assertEquals((clientNumber * (clientNumber + 1)) / 2, sum)
    }

    @Test
    @Timeout(60)
    fun `gateway to multiple servers`() {
        val gatewayAddress = Pair("localhost", 10000)
        val serverAddresses = listOf(
            "http://127.0.0.1:10001",
            "http://127.0.0.1:10002",
            "http://127.0.0.1:10003",
            "http://127.0.0.1:10004")
        // We first produce some messages which will be consumed by the Gateway.
        val messageCount = 10000 // this number will be produced for each target
        val deliveryLatch = CountDownLatch(serverAddresses.size * messageCount)
        val servers = mutableListOf<HttpServer>()
        repeat(serverAddresses.size) { id ->
            repeat(messageCount) {
                val msg = LinkOutMessage.newBuilder().apply {
                    header = LinkOutHeader("Chip", NetworkType.CORDA_5, serverAddresses[id])
                    payload = authenticatedP2PMessage("Target-${serverAddresses[id]}")
                }.build()
                topicServiceAlice!!.addRecords(listOf(Record(LINK_OUT_TOPIC, "key", msg)))
            }
            val serverURI = URI.create(serverAddresses[id])
            servers.add(HttpServer(serverURI.host, serverURI.port, chipSslConfig).also {
                it.addListener(object : HttpEventListener {
                    override fun onMessage(message: HttpMessage) {
                        val p2pMessage = LinkInMessage.fromByteBuffer(ByteBuffer.wrap(message.payload))
                        assertEquals("Target-${serverAddresses[id]}", String((p2pMessage.payload as AuthenticatedDataMessage).payload.array()))
                        it.write(HttpResponseStatus.OK, ByteArray(0), message.source)
                        deliveryLatch.countDown()
                    }
                })
                it.start()
            })
        }

        var startTime: Long
        var endTime: Long
        Gateway(GatewayConfiguration(gatewayAddress.first, gatewayAddress.second, aliceSslConfig),
                SubscriptionFactoryStub(topicServiceAlice!!),
                PublisherFactoryStub(topicServiceAlice!!)
        ).use {
            startTime = Instant.now().toEpochMilli()
            it.start()
            // Wait until all messages have been delivered
            deliveryLatch.await()
            endTime = Instant.now().toEpochMilli()
        }

        servers.forEach { it.stop() }
        println("Done sending ${messageCount * serverAddresses.size} messages in ${endTime - startTime} milliseconds")
    }

    @Test
    fun `gateway to gateway - dual stream`() {
        val aliceGatewayAddress = URI.create("http://127.0.0.1:10003")
        val bobGatewayAddress = URI.create("http://127.0.0.1:10004")
        val messageCount = 10000
        topicServiceAlice!!.addRecords(listOf(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1)))))
        topicServiceBob!!.addRecords(listOf(Record(SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(listOf(1)))))
        // Produce messages for each Gateway
        repeat(messageCount) {
            var msg = LinkOutMessage.newBuilder().apply {
                header = LinkOutHeader("Dale", NetworkType.CORDA_5, bobGatewayAddress.toString())
                payload = authenticatedP2PMessage("Target-$bobGatewayAddress")
            }.build()
            topicServiceAlice!!.addRecords(listOf(Record(LINK_OUT_TOPIC, "key", msg)))

            msg = LinkOutMessage.newBuilder().apply {
                header = LinkOutHeader("Chip", NetworkType.CORDA_5, aliceGatewayAddress.toString())
                payload = authenticatedP2PMessage("Target-$aliceGatewayAddress")
            }.build()
            topicServiceBob!!.addRecords(listOf(Record(LINK_OUT_TOPIC, "key", msg)))
        }

        val receivedLatch = CountDownLatch(messageCount * 2)
        (topicServiceAlice as TopicServiceStub).onPublish.subscribe { records ->
            val inRecords = records.filter { it.topic == LINK_IN_TOPIC }
            if (inRecords.isNotEmpty()) receivedLatch.countDown()
        }
        (topicServiceBob as TopicServiceStub).onPublish.subscribe { records ->
            val inRecords = records.filter { it.topic == LINK_IN_TOPIC }
            if (inRecords.isNotEmpty()) receivedLatch.countDown()
        }

        val startTime = Instant.now().toEpochMilli()
        val barrier = CountDownLatch(1)
        // Start the gateways
        val t1 = thread {
            val alice = Gateway(GatewayConfiguration(aliceGatewayAddress.host, aliceGatewayAddress.port, chipSslConfig),
                SubscriptionFactoryStub(topicServiceAlice!!),
                PublisherFactoryStub(topicServiceAlice!!)
            ).also { it.start() }
            barrier.await()
            alice.stop()
        }
        val t2 = thread {
            val bob = Gateway(GatewayConfiguration(bobGatewayAddress.host, bobGatewayAddress.port, daleSslConfig),
                SubscriptionFactoryStub(topicServiceBob!!),
                PublisherFactoryStub(topicServiceBob!!)
            ).also { it.start() }
            barrier.await()
            bob.stop()
        }

        receivedLatch.await()
        barrier.countDown()
        val endTime = Instant.now().toEpochMilli()
        t1.join()
        t2.join()
        println("Done processing ${messageCount * 2} in ${endTime - startTime} milliseconds.")
    }

    private fun authenticatedP2PMessage(content: String) = AuthenticatedDataMessage.newBuilder().apply {
            header = CommonHeader(MessageType.DATA, 0, sessionId, 1L, Instant.now().toEpochMilli())
            payload = ByteBuffer.wrap(content.toByteArray())
            authTag = ByteBuffer.wrap(ByteArray(0))
    }.build()
}