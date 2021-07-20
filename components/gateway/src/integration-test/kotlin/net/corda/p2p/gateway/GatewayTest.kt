package net.corda.p2p.gateway

import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.messaging.api.records.Record
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.MessageType
import net.corda.p2p.gateway.Gateway.Companion.CONSUMER_GROUP_ID
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import net.corda.p2p.schema.Schema.Companion.LINK_OUT_TOPIC
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.security.KeyStore
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Flow
import kotlin.concurrent.thread

class GatewayTest {

    private val keystorePass = "cordacadevpass"
    private val truststorePass = "trustpass"
    private val sslConfiguration = object : SslConfiguration {
        override val keyStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("sslkeystore.jks")!!.file), keystorePass.toCharArray())
        }
        override val keyStorePassword: String = keystorePass
        override val trustStore: KeyStore = KeyStore.getInstance("JKS").also {
            it.load(FileInputStream(javaClass.classLoader.getResource("truststore.jks")!!.file), truststorePass.toCharArray())
        }
        override val trustStorePassword: String = truststorePass
    }

    private var topicServiceAlice: TopicService? = null
    private var topicServiceBob: TopicService? = null

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
        val serverAddress = URI.create("http://localhost:10000")
        val message = LinkInMessage(authenticatedP2PMessage(String()))
        Gateway(serverAddress.host,
                serverAddress.port,
                sslConfiguration,
                SubscriptionFactoryStub(topicServiceAlice!!),
                PublisherFactoryStub(topicServiceAlice!!)
        ).use {
            it.start()
            HttpClient(serverAddress, sslConfiguration).use { client->
                client.start()
                val responseReceived = CountDownLatch(1)
                client.onConnection.subscribe { evt ->
                    if (evt.connected) {
                        client.send(message.toByteBuffer().array())
                    }
                }
                client.onReceive.subscribe { msg ->
                    assertEquals(InetSocketAddress(serverAddress.host, serverAddress.port) as SocketAddress, msg.source)
                    assertEquals(HttpResponseStatus.OK, msg.statusCode)
                    assertTrue(msg.payload.isEmpty())
                    responseReceived.countDown()
                }
                responseReceived.await()
            }
        }

        // Verify Gateway has successfully forwarded the message to the P2P_IN topic
        val publishedRecords = topicServiceAlice!!.getRecords(LINK_IN_TOPIC, CONSUMER_GROUP_ID, -1, false)
        assertEquals(1, publishedRecords.size)
        val receivedMessage = publishedRecords.first().record.value
        assertTrue(receivedMessage is LinkInMessage)
        val payload = (receivedMessage as LinkInMessage).payload as AuthenticatedDataMessage
        assertEquals(message.payload, payload)
    }


    @Test
    @Timeout(60)
    fun `multiple clients to gateway`() {
        val clientNumber = 4
        val threadPool = NioEventLoopGroup(clientNumber)
        val serverAddress = URI.create("http://localhost:10000")
        val clients = mutableListOf<HttpClient>()
        Gateway(serverAddress.host, serverAddress.port,
                sslConfiguration,
                SubscriptionFactoryStub(topicServiceAlice!!),
                PublisherFactoryStub(topicServiceAlice!!)).use {
            it.start()
            val responseReceived = CountDownLatch(clientNumber)
            repeat(clientNumber) { index ->
                val client = HttpClient(serverAddress, sslConfiguration, threadPool)
                client.onConnection.subscribe { evt ->
                    if (evt.connected) {
                        val p2pOutMessage = LinkInMessage(authenticatedP2PMessage("Client-${index + 1}"))
                        client.send(p2pOutMessage.toByteBuffer().array())
                    }
                }
                client.onReceive.subscribe { msg ->
                    assertEquals(InetSocketAddress(serverAddress.host, serverAddress.port) as SocketAddress, msg.source)
                    assertEquals(HttpResponseStatus.OK, msg.statusCode)
                    assertTrue(msg.payload.isEmpty())
                    responseReceived.countDown()
                }
                client.start()
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
        val serverAddresses = listOf("http://localhost:10001", "http://localhost:10002", "http://localhost:10003", "http://localhost:10004")
        // We first produce some messages which will be consumed by the Gateway.
        val messageCount = 10000 // this number will be produced for each target
        val deliveryLatch = CountDownLatch(serverAddresses.size * messageCount)
        val servers = mutableListOf<HttpServer>()
        repeat(serverAddresses.size) { id ->
            repeat(messageCount) {
                val msg = LinkOutMessage.newBuilder().apply {
                    header = LinkOutHeader("PartyA", NetworkType.CORDA_4, serverAddresses[id])
                    payload = authenticatedP2PMessage("Target-${serverAddresses[id]}")
                }.build()
                topicServiceAlice!!.addRecords(listOf(Record(LINK_OUT_TOPIC, "key", msg)))
            }
            val serverURI = URI.create(serverAddresses[id])
            servers.add(HttpServer(serverURI.host, serverURI.port, sslConfiguration).also {
                it.registerMessageSubscriber(object : Flow.Subscriber<HttpMessage> {
                    var sub: Flow.Subscription? = null
                    override fun onSubscribe(subscription: Flow.Subscription) {
                        sub = subscription
                        subscription.request(1)
                    }

                    override fun onNext(item: HttpMessage) {
                        val p2pMessage = LinkInMessage.fromByteBuffer(ByteBuffer.wrap(item.payload))
                        assertEquals("Target-${serverAddresses[id]}", String((p2pMessage.payload as AuthenticatedDataMessage).payload.array()))
                        it.write(HttpResponseStatus.OK, ByteArray(0), item.source)
                        deliveryLatch.countDown()
                        sub?.request(1)
                    }

                    override fun onError(throwable: Throwable?) = Unit
                    override fun onComplete() = Unit
                })

                it.start()
            })
        }

        var startTime: Long
        var endTime: Long
        Gateway(gatewayAddress.first,
                gatewayAddress.second,
                sslConfiguration,
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
        val aliceGatewayAddress = URI.create("http://localhost:10001")
        val bobGatewayAddress = URI.create("http://localhost:10002")
        val messageCount = 10000
        // Produce messages for each Gateway
        repeat(messageCount) {
            var msg = LinkOutMessage.newBuilder().apply {
                header = LinkOutHeader("PartyA", NetworkType.CORDA_4, bobGatewayAddress.toString())
                payload = authenticatedP2PMessage("Target-$bobGatewayAddress")
            }.build()
            topicServiceAlice!!.addRecords(listOf(Record(LINK_OUT_TOPIC, "key", msg)))

            msg = LinkOutMessage.newBuilder().apply {
                header = LinkOutHeader("PartyA", NetworkType.CORDA_4, aliceGatewayAddress.toString())
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
            val alice = Gateway(
                aliceGatewayAddress.host,
                aliceGatewayAddress.port,
                sslConfiguration,
                SubscriptionFactoryStub(topicServiceAlice!!),
                PublisherFactoryStub(topicServiceAlice!!)
            ).also { it.start() }
            barrier.await()
            alice.stop()
        }
        val t2 = thread {
            val bob = Gateway(
                bobGatewayAddress.host,
                bobGatewayAddress.port,
                sslConfiguration,
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
            header = CommonHeader(MessageType.DATA, 0, "session-1", 1L, Instant.now().toEpochMilli())
            payload = ByteBuffer.wrap(content.toByteArray())
            authTag = ByteBuffer.wrap(ByteArray(0))
    }.build()
}