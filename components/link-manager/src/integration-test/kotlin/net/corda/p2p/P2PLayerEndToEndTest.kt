@file:Suppress("DEPRECATION")

package net.corda.p2p

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.read.impl.ConfigurationReadServiceImpl
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfigFactoryImpl
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.publish.ConfigPublisher
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.libs.configuration.publish.impl.ConfigPublisherImpl
import net.corda.libs.configuration.read.kafka.factory.ConfigReaderFactoryImpl
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.HEARTBEAT_MESSAGE_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITIES_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_GPOUP_ID
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_X500_NAME
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_MESSAGE_SIZE_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MESSAGE_REPLAY_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.PROTOCOL_MODE_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_TIMEOUT_KEY
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.gateway.Gateway
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.linkmanager.ConfigBasedLinkManagerHostingMap
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.StubCryptoService
import net.corda.p2p.linkmanager.StubNetworkMap
import net.corda.p2p.schema.Schema
import net.corda.p2p.schema.TestSchema
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.KeyPairEntry
import net.corda.p2p.test.NetworkMapEntry
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import net.corda.v5.base.util.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class P2PLayerEndToEndTest {

    companion object {
        private const val TTL = 1_000_000L
        private const val SUBSYSTEM = "e2e.test.app"
        private val logger = contextLogger()
        private const val CONFIG_TOPIC_NAME = "config"
        private const val GROUP_ID = "group-1"
    }
    private val bootstrapConfig = SmartConfigFactoryImpl().create(ConfigFactory.empty()
        .withValue(
            "config.topic.name",
            ConfigValueFactory.fromAnyRef(CONFIG_TOPIC_NAME)
        ))

    private val hostA = Host("www.alice.net", 10500, "O=Alice, L=London, C=GB", "sslkeystore_alice", "truststore")
    private val hostB = Host("chip.net", 10501, "O=Chip, L=London, C=GB", "sslkeystore_chip", "truststore")

    @BeforeEach
    fun setup() {
        hostA.configReadService.start()
        hostA.configReadService.bootstrapConfig(bootstrapConfig)
        hostB.configReadService.start()
        hostB.configReadService.bootstrapConfig(bootstrapConfig)
    }

    @Test
    @Timeout(60)
    fun `two hosts can exchange data messages over the p2p layer successfully`() {
        val hostALinkManager = createLinkManager(hostA.subscriptionFactory, hostA.publisherFactory, hostA.lifecycleCoordinatorFactory, hostA.configReadService)
        val hostAGateway = Gateway(hostA.configReadService, hostA.subscriptionFactory, hostA.publisherFactory, hostA.lifecycleCoordinatorFactory, SmartConfigImpl.empty(), 1)
        val hostBLinkManager = createLinkManager(hostB.subscriptionFactory, hostB.publisherFactory, hostB.lifecycleCoordinatorFactory, hostB.configReadService)
        val hostBGateway = Gateway(hostB.configReadService, hostB.subscriptionFactory, hostB.publisherFactory, hostB.lifecycleCoordinatorFactory, SmartConfigImpl.empty(), 1)

        hostALinkManager.start()
        hostAGateway.start()
        hostBLinkManager.start()
        hostBGateway.start()

        publishConfig()
        publishNetworkMapAndKeys()

        eventually(30.seconds) {
            assertThat(hostALinkManager.isRunning).isTrue
            assertThat(hostAGateway.isRunning).isTrue
            assertThat(hostBLinkManager.isRunning).isTrue
            assertThat(hostBGateway.isRunning).isTrue
        }

        val hostAReceivedMessages = ConcurrentHashMap.newKeySet<String>()
        val hostAApplicationReader = hostA.subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("app-layer", Schema.P2P_IN_TOPIC, 1), InitiatorProcessor(hostAReceivedMessages),
            bootstrapConfig,
            null
        )
        val hostBApplicationReaderWriter = hostB.subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("app-layer", Schema.P2P_IN_TOPIC, 1), ResponderProcessor(),
            bootstrapConfig,
            null
        )
        hostAApplicationReader.start()
        hostBApplicationReaderWriter.start()

        val hostAApplicationWriter = hostA.publisherFactory.createPublisher(PublisherConfig("app-layer", 1), bootstrapConfig)
        val initialMessages = (1..10).map { index ->
            val randomId = UUID.randomUUID().toString()
            val messageHeader = AuthenticatedMessageHeader(HoldingIdentity(hostB.x500Name, GROUP_ID), HoldingIdentity(hostA.x500Name, GROUP_ID), TTL, randomId, randomId, SUBSYSTEM)
            val message = AuthenticatedMessage(messageHeader, ByteBuffer.wrap("ping ($index)".toByteArray()))
            Record(Schema.P2P_OUT_TOPIC, randomId, AppMessage(message))
        }
        hostAApplicationWriter.use {
            hostAApplicationWriter.start()
            val futures = hostAApplicationWriter.publish(initialMessages)
            futures.forEach { it.get() }
        }

        eventually(10.seconds) {
            (1..10).forEach { messageNo ->
                assertTrue(hostAReceivedMessages.contains("pong ($messageNo)"), "No reply received for message $messageNo")
            }
        }

        hostAApplicationReader.stop()
        hostBApplicationReaderWriter.stop()

        hostALinkManager.close()
        hostAGateway.close()
        hostBLinkManager.close()
        hostBGateway.close()
    }

    private fun createLinkManager(subscriptionFactory: InMemSubscriptionFactory, publisherFactory: PublisherFactory, coordinatorFactory: LifecycleCoordinatorFactory, configReadService: ConfigurationReadService): LinkManager {
        return LinkManager(subscriptionFactory, publisherFactory, coordinatorFactory, configReadService,
            SmartConfigImpl.empty(), 1, StubNetworkMap(coordinatorFactory, subscriptionFactory, 1, bootstrapConfig),
            ConfigBasedLinkManagerHostingMap(configReadService, coordinatorFactory), StubCryptoService(coordinatorFactory, subscriptionFactory, 1, bootstrapConfig)
        )
    }

    private fun publishNetworkMapAndKeys() {
        val publisherForHostA = hostA.publisherFactory.createPublisher(PublisherConfig("test-runner-publisher", 1), bootstrapConfig)
        val publisherForHostB = hostB.publisherFactory.createPublisher(PublisherConfig("test-runner-publisher", 1), bootstrapConfig)
        val networkMapEntries = mapOf(
            "${hostA.x500Name}-$GROUP_ID" to NetworkMapEntry(net.corda.data.identity.HoldingIdentity(hostA.x500Name, GROUP_ID), ByteBuffer.wrap(hostA.keyPair.public.encoded), KeyAlgorithm.ECDSA, "http://${hostA.p2pAddress}:${hostA.p2pPort}", NetworkType.CORDA_5),
            "${hostB.x500Name}-$GROUP_ID" to NetworkMapEntry(net.corda.data.identity.HoldingIdentity(hostB.x500Name, GROUP_ID), ByteBuffer.wrap(hostB.keyPair.public.encoded), KeyAlgorithm.ECDSA, "http://${hostB.p2pAddress}:${hostB.p2pPort}", NetworkType.CORDA_5)
        )
        val networkMapRecords = networkMapEntries.map { Record(TestSchema.NETWORK_MAP_TOPIC, it.key, it.value) }
        publisherForHostA.use { publisher ->
            publisher.start()
            publisher.publish(networkMapRecords).forEach { it.get() }
            publisher.publish(listOf(
                Record(TestSchema.CRYPTO_KEYS_TOPIC, "key-1", KeyPairEntry(KeyAlgorithm.ECDSA, ByteBuffer.wrap(hostA.keyPair.public.encoded), ByteBuffer.wrap(hostA.keyPair.private.encoded)))
            )).forEach { it.get() }
        }
        publisherForHostB.use { publisher ->
            publisher.start()
            publisher.publish(networkMapRecords).forEach { it.get() }
            publisher.publish(listOf(
                Record(TestSchema.CRYPTO_KEYS_TOPIC, "key-1", KeyPairEntry(KeyAlgorithm.ECDSA, ByteBuffer.wrap(hostB.keyPair.public.encoded), ByteBuffer.wrap(hostB.keyPair.private.encoded)))
            )).forEach { it.get() }
        }
    }

    private fun publishConfig() {
        publishGatewayConfig(hostA.configPublisher, hostA.gatewayConfig)
        publishLinkManagerConfig(hostA.configPublisher, hostA.linkManagerConfig)
        publishGatewayConfig(hostB.configPublisher, hostB.gatewayConfig)
        publishLinkManagerConfig(hostB.configPublisher, hostB.linkManagerConfig)
    }

    private fun publishGatewayConfig(configPublisher: ConfigPublisher, gatewayConfig: Config) {
        configPublisher.updateConfiguration(
            CordaConfigurationKey(
                "p2p-e2e-test-runner",
                CordaConfigurationVersion("p2p", 0, 1),
                CordaConfigurationVersion("gateway", 0, 1)
            ),
            gatewayConfig
        )
    }

    private fun publishLinkManagerConfig(configPublisher: ConfigPublisher, linkManagerConfig: Config) {
        configPublisher.updateConfiguration(
            CordaConfigurationKey(
                "p2p-e2e-test-runner",
                CordaConfigurationVersion(LinkManagerConfiguration.PACKAGE_NAME, 0, 1),
                CordaConfigurationVersion(LinkManagerConfiguration.COMPONENT_NAME, 0, 1)
            ),
            linkManagerConfig
        )
    }

    private class InitiatorProcessor(val receivedMessages: ConcurrentHashMap.KeySetView<String, Boolean>): DurableProcessor<String, AppMessage> {

        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<AppMessage>
            get() = AppMessage::class.java

        override fun onNext(events: List<Record<String, AppMessage>>): List<Record<*, *>> {
            events.forEach {
                val message = it.value!!.message as AuthenticatedMessage
                receivedMessages.add(message.payload.array().toString(Charsets.UTF_8))
                logger.info("Received message: ${message.payload.array().toString(Charsets.UTF_8)}")
            }
            return emptyList()
        }

    }

    private class ResponderProcessor: DurableProcessor<String, AppMessage> {

        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<AppMessage>
            get() = AppMessage::class.java

        override fun onNext(events: List<Record<String, AppMessage>>): List<Record<*, *>> {
            return events.map {
                val message = it.value!!.message as AuthenticatedMessage
                val randomId = UUID.randomUUID().toString()
                logger.info("Received message: ${message.payload.array().toString(Charsets.UTF_8)} and responding")
                val responseMessage = AuthenticatedMessage(
                    AuthenticatedMessageHeader(message.header.source, message.header.destination, TTL, randomId, randomId, SUBSYSTEM),
                    ByteBuffer.wrap(message.payload.array().toString(Charsets.UTF_8).replace("ping", "pong").toByteArray())
                )
                Record(Schema.P2P_OUT_TOPIC, randomId, AppMessage(responseMessage))
            }
        }

    }

    private class Host(val p2pAddress: String, val p2pPort: Int, val x500Name: String, keyStoreFileName: String, trustStoreFileName: String) {
        companion object {
            private val linkManagerConfigTemplate = """
                {
                    $LOCALLY_HOSTED_IDENTITIES_KEY: [
                        {
                            "$LOCALLY_HOSTED_IDENTITY_X500_NAME": "<x500-name>",
                            "$LOCALLY_HOSTED_IDENTITY_GPOUP_ID": "$GROUP_ID"
                        }
                    ],
                    $MAX_MESSAGE_SIZE_KEY: 1000000,
                    $PROTOCOL_MODE_KEY: ["${ProtocolMode.AUTHENTICATION_ONLY}", "${ProtocolMode.AUTHENTICATED_ENCRYPTION}"],
                    $MESSAGE_REPLAY_PERIOD_KEY: 2000,
                    $HEARTBEAT_MESSAGE_PERIOD_KEY: 2000,
                    $SESSION_TIMEOUT_KEY: 10000
                }
            """.trimIndent()
        }

        private val sslConfig = SslConfiguration(
            keyStorePassword = "password",
            rawKeyStore = readKeyStore(keyStoreFileName),
            trustStorePassword = "password",
            rawTrustStore = readKeyStore(trustStoreFileName),
            revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL)
        )
        val keyPair = KeyPairGenerator.getInstance("EC").genKeyPair()
        val topicService = TopicServiceImpl()
        val lifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
        val subscriptionFactory = InMemSubscriptionFactory(topicService, RPCTopicServiceImpl(), lifecycleCoordinatorFactory)
        val publisherFactory = CordaPublisherFactory(topicService, RPCTopicServiceImpl(), lifecycleCoordinatorFactory)
        val configReadService = ConfigurationReadServiceImpl(lifecycleCoordinatorFactory, ConfigReaderFactoryImpl(subscriptionFactory, SmartConfigFactoryImpl()))
        val configPublisher = publisherFactory.createPublisher(PublisherConfig("config-writer")).let {
            ConfigPublisherImpl(CONFIG_TOPIC_NAME, it)
        }
        val gatewayConfig = createGatewayConfig(p2pPort, p2pAddress, sslConfig)
        val linkManagerConfig = ConfigFactory.parseString(linkManagerConfigTemplate.replace("<x500-name>", x500Name))


        private fun readKeyStore(fileName: String): ByteArray {
            return javaClass.classLoader.getResource("$fileName.jks").readBytes()
        }

        private fun createGatewayConfig(port: Int, domainName: String, sslConfig: SslConfiguration): Config {
            return ConfigFactory.empty()
                .withValue("hostAddress", ConfigValueFactory.fromAnyRef(domainName))
                .withValue("hostPort", ConfigValueFactory.fromAnyRef(port))
                .withValue("sslConfig.keyStorePassword", ConfigValueFactory.fromAnyRef(sslConfig.keyStorePassword))
                .withValue("sslConfig.keyStore", ConfigValueFactory.fromAnyRef(sslConfig.rawKeyStore.toBase64()))
                .withValue("sslConfig.trustStorePassword", ConfigValueFactory.fromAnyRef(sslConfig.trustStorePassword))
                .withValue("sslConfig.trustStore", ConfigValueFactory.fromAnyRef(sslConfig.rawTrustStore.toBase64()))
                .withValue("sslConfig.revocationCheck.mode", ConfigValueFactory.fromAnyRef(sslConfig.revocationCheck.mode.toString()))
        }
    }

}