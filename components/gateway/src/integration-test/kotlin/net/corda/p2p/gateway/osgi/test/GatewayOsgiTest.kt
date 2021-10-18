package net.corda.p2p.gateway.osgi.test

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.http.HttpResponseStatus
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import net.corda.libs.configuration.write.factory.ConfigWriterFactory
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.SessionPartitions
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.CommonHeader
import net.corda.p2p.crypto.MessageType
import net.corda.p2p.gateway.Gateway
import net.corda.p2p.gateway.messaging.GatewayConfiguration
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.http.DestinationInfo
import net.corda.p2p.gateway.messaging.http.HttpClient
import net.corda.p2p.gateway.messaging.http.HttpEventListener
import net.corda.p2p.gateway.messaging.http.HttpMessage
import net.corda.p2p.gateway.messaging.http.HttpServer
import net.corda.p2p.schema.Schema
import net.corda.p2p.schema.Schema.Companion.LINK_IN_TOPIC
import net.corda.p2p.schema.Schema.Companion.LINK_OUT_TOPIC
import net.corda.test.util.eventually
import net.corda.v5.base.util.seconds
import net.corda.v5.base.util.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.io.File
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
@Timeout(1, unit = TimeUnit.MINUTES)
class GatewayOsgiTest {
    companion object {
        @InjectService(timeout = 4000)
        lateinit var configurationReadService: ConfigurationReadService

        @InjectService(timeout = 4000)
        lateinit var gateway: Gateway

        @InjectService(timeout = 4000)
        lateinit var configWriterFactory: ConfigWriterFactory

        private const val configTopicName = "topic.name"

        private const val host = "www.alice.net"
        private const val port = 33122

        private val bundle by lazy {
            FrameworkUtil
                .getBundle(GatewayOsgiTest::class.java)
        }

        private fun bootstrapConfiguration() {
            configurationReadService.start()
            val bootstrapper = ConfigFactory.empty()
                .withValue(
                    "config.topic.name",
                    ConfigValueFactory.fromAnyRef(configTopicName)
                )

            configurationReadService.bootstrapConfig(bootstrapper)
        }

        private fun loadHostFiles() {
            val hostsFile = File.createTempFile("hosts", "txt")
            val hosts = bundle.getResource("hosts").readBytes()
            hostsFile.deleteOnExit()
            hostsFile.writeBytes(hosts)
            System.setProperty("jdk.net.hosts.file", hostsFile.absolutePath)
        }
        private val aliceSslConfig by lazy {
            SslConfiguration(
                keyStorePassword = "password",
                trustStorePassword = "password",
                rawKeyStore = bundle
                    .getResource("sslkeystore_alice.jks")
                    .readBytes(),
                rawTrustStore = bundle
                    .getResource("truststore.jks")
                    .readBytes(),
                revocationCheck = RevocationConfig(RevocationConfigMode.OFF)
            )
        }

        private fun publishConfiguration() {
            bootstrapConfiguration()
            val publishConfig = ConfigFactory.empty()
                .withValue(
                    "hostAddress",
                    ConfigValueFactory.fromAnyRef(host)
                )
                .withValue(
                    "hostPort",
                    ConfigValueFactory.fromAnyRef(port)
                )
                .withValue(
                    "traceLogging",
                    ConfigValueFactory.fromAnyRef(true)
                )
                .withValue(
                    "sslConfig.keyStorePassword",
                    ConfigValueFactory.fromAnyRef(aliceSslConfig.keyStorePassword)
                )
                .withValue(
                    "sslConfig.keyStore",
                    ConfigValueFactory.fromAnyRef(
                        aliceSslConfig.rawKeyStore.toBase64()
                    )
                )
                .withValue(
                    "sslConfig.trustStorePassword",
                    ConfigValueFactory.fromAnyRef(aliceSslConfig.trustStorePassword)
                )
                .withValue(
                    "sslConfig.trustStore",
                    ConfigValueFactory.fromAnyRef(
                        aliceSslConfig.rawTrustStore.toBase64()
                    )
                )
                .withValue(
                    "sslConfig.revocationCheck.mode",
                    ConfigValueFactory.fromAnyRef(aliceSslConfig.revocationCheck.mode.toString())
                )
            val configWriter = configWriterFactory.createWriter(configTopicName, ConfigFactory.empty())
            configWriter.updateConfiguration(
                CordaConfigurationKey(
                    "myKey",
                    CordaConfigurationVersion("p2p", 0, 1),
                    CordaConfigurationVersion("gateway", 0, 1)
                ),
                publishConfig
            )
        }

        fun startGateway() {
            publishConfiguration()
            gateway.start()
            eventually(duration = 30.seconds) {
                assertThat(gateway.isRunning).isTrue
            }
        }

        @BeforeAll
        @JvmStatic
        fun startUp() {
            loadHostFiles()
            startGateway()
        }

        @AfterAll
        @JvmStatic
        fun clearBootstrapper() {
            configurationReadService.close()
            gateway.close()
        }
    }

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @Test
    fun `gateway outbound path`() {
        val otherPort = 43432
        val sessionId = "Session-3"

        val messageToSend = AuthenticatedDataMessage.newBuilder().apply {
            header = CommonHeader(MessageType.DATA, 0, sessionId, 1L, Instant.now().toEpochMilli())
            payload = ByteBuffer.wrap(ByteArray(0))
            authTag = ByteBuffer.wrap(ByteArray(0))
        }.build()
        val linkOutMessage = LinkOutMessage.newBuilder().apply {
            header = LinkOutHeader("", NetworkType.CORDA_5, "http://$host:$otherPort")
            payload = messageToSend
        }.build()

        val source = CompletableFuture<SocketAddress>()
        val listener = object : HttpEventListener {
            override fun onMessage(message: HttpMessage) {
                assertThat(message.statusCode).isEqualTo(HttpResponseStatus.OK)
                assertThat(message.destination).isInstanceOfSatisfying(InetSocketAddress::class.java) {
                    assertThat(it.port).isEqualTo(otherPort)
                }
                val linkInMessage = LinkInMessage.fromByteBuffer(ByteBuffer.wrap(message.payload))
                assertThat(linkInMessage.payload).isEqualTo(messageToSend)
                source.complete(message.source)
            }
        }
        HttpServer(listener, GatewayConfiguration(host, otherPort, aliceSslConfig)).use { server ->
            server.start()
            publisherFactory.createPublisher(PublisherConfig("testOut")).use {
                it.publish(listOf(Record(LINK_OUT_TOPIC, "key1", linkOutMessage)))
            }

            assertThat(source.join()).isNotNull

            server.write(HttpResponseStatus.OK, ByteArray(0), source.get())
        }
    }

    @Test
    fun `gateway inbound path`() {
        val sessionId = "session-2"
        publisherFactory.createPublisher(PublisherConfig("publish sessions")).use { publisher ->
            publisher.publish(
                listOf(
                    Record(
                        Schema.SESSION_OUT_PARTITIONS,
                        sessionId,
                        SessionPartitions(listOf(5))
                    )
                )
            ).forEach { it.get() }
        }

        val latch = CountDownLatch(2)
        val message = AuthenticatedDataMessage.newBuilder().apply {
            header = CommonHeader(MessageType.DATA, 0, sessionId, 1L, Instant.now().toEpochMilli())
            payload = ByteBuffer.wrap(ByteArray(0))
            authTag = ByteBuffer.wrap(ByteArray(0))
        }.build()
        val linkInMessage = LinkInMessage(message)

        val subscription = subscriptionFactory.createEventLogSubscription(
            SubscriptionConfig("group.one", Schema.LINK_IN_TOPIC),
            processor = object : EventLogProcessor<String, LinkInMessage> {

                override val keyClass = String::class.java
                override val valueClass = LinkInMessage::class.java
                override fun onNext(events: List<EventLogRecord<String, LinkInMessage>>): List<Record<*, *>> {
                    assertThat(events).hasSize(1).anyMatch {
                        it.key == sessionId &&
                            it.value == linkInMessage &&
                            it.partition == 5
                    }
                    latch.countDown()
                    return emptyList()
                }
            },
            partitionAssignmentListener = null
        )
        subscription.start()

        val listener = object : HttpEventListener {
            override fun onMessage(message: HttpMessage) {
                assertThat(message.statusCode).isEqualTo(HttpResponseStatus.OK)
                latch.countDown()
            }
        }
        HttpClient(
            DestinationInfo(
                URI("http://$host:$port"),
                host, null
            ),
            aliceSslConfig,
            NioEventLoopGroup(1),
            NioEventLoopGroup(1),
            listener
        ).use { client ->
            client.start()
            client.write(linkInMessage.toByteBuffer().array())
            latch.await()
        }
    }
}
