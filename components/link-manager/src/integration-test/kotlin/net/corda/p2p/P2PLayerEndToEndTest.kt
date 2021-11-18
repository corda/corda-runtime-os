package net.corda.p2p

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.read.impl.ConfigurationReadServiceImpl
import net.corda.libs.configuration.SmartConfigFactoryImpl
import net.corda.libs.configuration.SmartConfigImpl
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
import net.corda.libs.configuration.write.ConfigWriter
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import net.corda.libs.configuration.write.kafka.ConfigWriterImpl
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
import net.corda.p2p.app.HoldingIdentity
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
    }

    private val configTopicName = "config"
    private val bootstrapConfig = SmartConfigFactoryImpl().create(ConfigFactory.empty()
        .withValue(
            "config.topic.name",
            ConfigValueFactory.fromAnyRef(configTopicName)
        ))
    private val groupId = "group-1"

    private val linkManagerConfig = """
            {
                $LOCALLY_HOSTED_IDENTITIES_KEY: [
                    {
                        "$LOCALLY_HOSTED_IDENTITY_X500_NAME": "<x500-name>",
                        "$LOCALLY_HOSTED_IDENTITY_GPOUP_ID": "$groupId"
                    }
                ],
                $MAX_MESSAGE_SIZE_KEY: 1000000,
                $PROTOCOL_MODE_KEY: ["${ProtocolMode.AUTHENTICATION_ONLY}", "${ProtocolMode.AUTHENTICATED_ENCRYPTION}"],
                $MESSAGE_REPLAY_PERIOD_KEY: 2000,
                $HEARTBEAT_MESSAGE_PERIOD_KEY: 2000,
                $SESSION_TIMEOUT_KEY: 10000
            }
        """.trimIndent()


    private val hostAPort = 10000
    private val hostADomainName = "www.alice.net"
    private val aliceX500Name = "O=Alice, L=London, C=GB"
    private val hostASslConfig = SslConfiguration(
        keyStorePassword = "password",
        rawKeyStore = readKeyStore("sslkeystore_alice"),
        trustStorePassword = "password",
        rawTrustStore = readKeyStore("truststore"),
        revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL)
    )
    private val aliceKeyPair = KeyPairGenerator.getInstance("EC").genKeyPair()
    private val hostATopicService = TopicServiceImpl()
    private val hostASubscriptionFactory = InMemSubscriptionFactory(hostATopicService, RPCTopicServiceImpl())
    private val hostAPublisherFactory = CordaPublisherFactory(hostATopicService, RPCTopicServiceImpl())
    private val hostALifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
    private val hostAConfigReadService = ConfigurationReadServiceImpl(hostALifecycleCoordinatorFactory, ConfigReaderFactoryImpl(hostASubscriptionFactory, SmartConfigFactoryImpl()))
    private val hostAConfigWriter = hostAPublisherFactory.createPublisher(PublisherConfig("config-writer")).let {
        ConfigWriterImpl(configTopicName, it)
    }
    private val hostAGatewayConfig = createGatewayConfig(hostAPort, hostADomainName, hostASslConfig)
    private val hostALinkManagerConfig = ConfigFactory.parseString(linkManagerConfig.replace("<x500-name>", aliceX500Name))

    private val hostBPort = 10001
    private val hostBDomainName = "chip.net"
    private val chipX500Name = "O=Chip, L=London, C=GB"
    private val hostBSslConfig = SslConfiguration(
        keyStorePassword = "password",
        rawKeyStore = readKeyStore("sslkeystore_chip"),
        trustStorePassword = "password",
        rawTrustStore = readKeyStore("truststore"),
        revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL)
    )
    private val chipKeyPair = KeyPairGenerator.getInstance("EC").genKeyPair()
    private val hostBTopicService = TopicServiceImpl()
    private val hostBSubscriptionFactory = InMemSubscriptionFactory(hostBTopicService, RPCTopicServiceImpl())
    private val hostBPublisherFactory = CordaPublisherFactory(hostBTopicService, RPCTopicServiceImpl())
    private val hostBLifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
    private val hostBConfigReadService = ConfigurationReadServiceImpl(hostBLifecycleCoordinatorFactory, ConfigReaderFactoryImpl(hostBSubscriptionFactory, SmartConfigFactoryImpl()))
    private val hostBConfigWriter = hostBPublisherFactory.createPublisher(PublisherConfig("config-writer")).let {
        ConfigWriterImpl(configTopicName, it)
    }
    private val hostBGatewayConfig = createGatewayConfig(hostBPort, hostBDomainName, hostBSslConfig)
    private val hostBLinkManagerConfig = ConfigFactory.parseString(linkManagerConfig.replace("<x500-name>", chipX500Name))

    @BeforeEach
    fun setup() {
        hostAConfigReadService.start()
        hostAConfigReadService.bootstrapConfig(bootstrapConfig)
        hostBConfigReadService.start()
        hostBConfigReadService.bootstrapConfig(bootstrapConfig)
    }

    @Test
    @Timeout(60)
    fun `two hosts can exchange data messages over the p2p layer successfully`() {
        val hostALinkManager = createLinkManager(hostASubscriptionFactory, hostAPublisherFactory, hostALifecycleCoordinatorFactory, hostAConfigReadService)
        val hostAGateway = Gateway(hostAConfigReadService, hostASubscriptionFactory, hostAPublisherFactory, hostALifecycleCoordinatorFactory, SmartConfigImpl.empty(), 1)
        val hostBLinkManager = createLinkManager(hostBSubscriptionFactory, hostBPublisherFactory, hostBLifecycleCoordinatorFactory, hostBConfigReadService)
        val hostBGateway = Gateway(hostBConfigReadService, hostBSubscriptionFactory, hostBPublisherFactory, hostBLifecycleCoordinatorFactory, SmartConfigImpl.empty(), 1)

        hostALinkManager.start()
        hostAGateway.start()
        hostBLinkManager.start()
        hostBGateway.start()

        publishConfig()
        publishNetworkMapAndKeys()

        eventually(30.seconds) {
            assertThat(hostAGateway.isRunning).isTrue
            assertThat(hostBGateway.isRunning).isTrue
        }

        val hostAReceivedMessages = ConcurrentHashMap.newKeySet<String>()
        val hostAApplicationReader = hostASubscriptionFactory.createDurableSubscription(
            SubscriptionConfig("app-layer", Schema.P2P_IN_TOPIC, 1), InitiatorProcessor(hostAReceivedMessages),
            bootstrapConfig,
            null
        )
        val hostBApplicationReaderWriter = hostBSubscriptionFactory.createDurableSubscription(
            SubscriptionConfig("app-layer", Schema.P2P_IN_TOPIC, 1), ResponderProcessor(),
            bootstrapConfig,
            null
        )
        hostAApplicationReader.start()
        hostBApplicationReaderWriter.start()

        val hostAApplicationWriter = hostAPublisherFactory.createPublisher(PublisherConfig("app-layer", 1), bootstrapConfig)
        val initialMessages = (1..10).map { index ->
            val randomId = UUID.randomUUID().toString()
            val messageHeader = AuthenticatedMessageHeader(HoldingIdentity(chipX500Name, groupId), HoldingIdentity(aliceX500Name, groupId), TTL, randomId, randomId, SUBSYSTEM)
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
            SmartConfigImpl.empty(), 1, StubNetworkMap(coordinatorFactory, subscriptionFactory, 1),
            ConfigBasedLinkManagerHostingMap(configReadService, coordinatorFactory), StubCryptoService(coordinatorFactory, subscriptionFactory, 1)
        )
    }

    private fun publishNetworkMapAndKeys() {
        val publisherForHostA = hostAPublisherFactory.createPublisher(PublisherConfig("test-runner-publisher", 1), bootstrapConfig)
        val publisherForHostB = hostBPublisherFactory.createPublisher(PublisherConfig("test-runner-publisher", 1), bootstrapConfig)
        val networkMapEntries = mapOf(
            "$aliceX500Name-$groupId" to NetworkMapEntry(net.corda.data.identity.HoldingIdentity(aliceX500Name, groupId), ByteBuffer.wrap(aliceKeyPair.public.encoded), KeyAlgorithm.ECDSA, "http://$hostADomainName:$hostAPort", NetworkType.CORDA_5),
            "$chipX500Name-$groupId" to NetworkMapEntry(net.corda.data.identity.HoldingIdentity(chipX500Name, groupId), ByteBuffer.wrap(chipKeyPair.public.encoded), KeyAlgorithm.ECDSA, "http://$hostBDomainName:$hostBPort", NetworkType.CORDA_5)
        )
        val networkMapRecords = networkMapEntries.map { Record(TestSchema.NETWORK_MAP_TOPIC, it.key, it.value) }
        publisherForHostA.use { publisher ->
            publisher.start()
            publisher.publish(networkMapRecords).forEach { it.get() }
            publisher.publish(listOf(
                Record(TestSchema.CRYPTO_KEYS_TOPIC, "key-1", KeyPairEntry(KeyAlgorithm.ECDSA, ByteBuffer.wrap(aliceKeyPair.public.encoded), ByteBuffer.wrap(aliceKeyPair.private.encoded)))
            )).forEach { it.get() }
        }
        publisherForHostB.use { publisher ->
            publisher.start()
            publisher.publish(networkMapRecords).forEach { it.get() }
            publisher.publish(listOf(
                Record(TestSchema.CRYPTO_KEYS_TOPIC, "key-1", KeyPairEntry(KeyAlgorithm.ECDSA, ByteBuffer.wrap(chipKeyPair.public.encoded), ByteBuffer.wrap(chipKeyPair.private.encoded)))
            )).forEach { it.get() }
        }
    }

    private fun publishConfig() {
        publishGatewayConfig(hostAConfigWriter, hostAGatewayConfig)
        publishLinkManagerConfig(hostAConfigWriter, hostALinkManagerConfig)
        publishGatewayConfig(hostBConfigWriter, hostBGatewayConfig)
        publishLinkManagerConfig(hostBConfigWriter, hostBLinkManagerConfig)
    }

    private fun publishGatewayConfig(configWriter: ConfigWriter, gatewayConfig: Config) {
        configWriter.updateConfiguration(
            CordaConfigurationKey(
                "p2p-e2e-test-runner",
                CordaConfigurationVersion("p2p", 0, 1),
                CordaConfigurationVersion("gateway", 0, 1)
            ),
            gatewayConfig
        )
    }

    private fun publishLinkManagerConfig(configWriter: ConfigWriter, linkManagerConfig: Config) {
        configWriter.updateConfiguration(
            CordaConfigurationKey(
                "p2p-e2e-test-runner",
                CordaConfigurationVersion(LinkManagerConfiguration.PACKAGE_NAME, 0, 1),
                CordaConfigurationVersion(LinkManagerConfiguration.COMPONENT_NAME, 0, 1)
            ),
            linkManagerConfig
        )
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

    private fun readKeyStore(fileName: String): ByteArray {
        return javaClass.classLoader.getResource("$fileName.jks").readBytes()
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

}