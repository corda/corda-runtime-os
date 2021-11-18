package net.corda.p2p

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
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
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import net.corda.libs.configuration.write.kafka.ConfigWriterImpl
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.EventLogRecord
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
import net.corda.v5.base.util.seconds
import net.corda.v5.base.util.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class P2Pe2eTests {

    companion object {
        private const val TTL = 1_000_000L
        private const val SUBSYSTEM = "e2e.test.app"
    }

    private val configTopicName = "config"
    private val bootstrapConfig = SmartConfigFactoryImpl().create(ConfigFactory.empty()
        .withValue(
            "config.topic.name",
            ConfigValueFactory.fromAnyRef(configTopicName)
        ))
    private val groupId = "group-1"


    private val hostAPort = 10000
    private val hostADomainName = "www.alice.net"
    private val hostASslConfig = SslConfiguration(
        keyStorePassword = "password",
        rawKeyStore = readKeyStore("sslkeystore_alice"),
        trustStorePassword = "password",
        rawTrustStore = readKeyStore("truststore"),
        revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL)
    )
    private val aliceX500Name = "O=Alice, L=London, C=GB"
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
    private val hostALinkManagerConfig = ConfigFactory.parseString(
        """
            {
                $LOCALLY_HOSTED_IDENTITIES_KEY: [
                    {
                        "$LOCALLY_HOSTED_IDENTITY_X500_NAME": "$aliceX500Name",
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
    )

    private val hostBPort = 10001
    private val hostBDomainName = "chip.net"
    private val hostBSslConfig = SslConfiguration(
        keyStorePassword = "password",
        rawKeyStore = readKeyStore("sslkeystore_chip"),
        trustStorePassword = "password",
        rawTrustStore = readKeyStore("truststore"),
        revocationCheck = RevocationConfig(RevocationConfigMode.HARD_FAIL)
    )
    private val chipX500Name = "O=Chip, L=London, C=GB"
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
    private val hostBLinkManagerConfig = ConfigFactory.parseString(
        """
            {
                $LOCALLY_HOSTED_IDENTITIES_KEY: [
                    {
                        "$LOCALLY_HOSTED_IDENTITY_X500_NAME": "$chipX500Name",
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
    )

    @Test
    fun `test compacted subscription`() {
        val map = mutableMapOf<String, String>()
        val topic = "topic"
        val publisher = hostAPublisherFactory.createPublisher(PublisherConfig("writer", 1), bootstrapConfig)
        val subscription = hostASubscriptionFactory.createCompactedSubscription(SubscriptionConfig("test-group", topic, 1), TestProcessor(map), bootstrapConfig)

        subscription.start()
        publisher.start()
        publisher.publish(listOf(Record(topic, "testkey", "testvalue")))

        eventually {
            assertTrue(map.isNotEmpty())
        }

        subscription.stop()
        publisher.close()
    }

    private class TestProcessor(val map: MutableMap<String, String>): CompactedProcessor<String, String> {
        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<String>
            get() = String::class.java

        override fun onSnapshot(currentData: Map<String, String>) {
            map.putAll(currentData)
        }

        override fun onNext(newRecord: Record<String, String>, oldValue: String?, currentData: Map<String, String>) {
            if (newRecord.value == null) {
                map.remove(newRecord.key)
            } else {
                map[newRecord.key] = newRecord.value!!
            }
        }

    }

    @Test
    fun `test event log subscription`() {
        val map = mutableMapOf<String, String>()
        val topic1 = "topic1"
        val topic2 = "topic2"

        val publisher = hostAPublisherFactory.createPublisher(PublisherConfig("writer", 1), bootstrapConfig)
        publisher.start()
        val copySubscription = hostASubscriptionFactory.createEventLogSubscription(SubscriptionConfig("test-group", topic1, 1), TestEventCopyProcessor(topic2), bootstrapConfig, null)
        copySubscription.start()
        val collectSubscription = hostASubscriptionFactory.createEventLogSubscription(SubscriptionConfig("test-group-2", topic2, 1), TestEventCollectProcessor(map), bootstrapConfig, null)
        collectSubscription.start()

        publisher.publish(listOf(Record(topic1, "testkey", "testvalue"))).forEach { it.get() }

        eventually {
            assertTrue(map.isNotEmpty())
        }
    }

    @Test
    fun `test event log with compacted subscription`() {
        val map = mutableMapOf<String, String>()
        val topic1 = "topic1"
        val topic2 = "topic2"

        val publisher = hostAPublisherFactory.createPublisher(PublisherConfig("writer", 1), bootstrapConfig)
        publisher.start()
        val copySubscription = hostASubscriptionFactory.createEventLogSubscription(SubscriptionConfig("test-group", topic1, 1), TestEventCopyProcessor(topic2), bootstrapConfig, null)
        copySubscription.start()
        val collectSubscription = hostASubscriptionFactory.createCompactedSubscription(SubscriptionConfig("test-group-2", topic2, 1), TestCompactedCollectProcessor(map), bootstrapConfig)
        collectSubscription.start()

        publisher.publish(listOf(Record(topic1, "testkey", "testvalue"))).forEach { it.get() }

        eventually {
            assertTrue(map.isNotEmpty())
        }
    }

    private class TestEventCopyProcessor(val toTopic: String): EventLogProcessor<String, String> {
        override fun onNext(events: List<EventLogRecord<String, String>>): List<Record<*, *>> {
            return events.map { Record(toTopic, it.key, it.value) }
        }

        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<String>
            get() = String::class.java

    }

    private class TestEventCollectProcessor(val map: MutableMap<String, String>): EventLogProcessor<String, String> {
        override fun onNext(events: List<EventLogRecord<String, String>>): List<Record<*, *>> {
            events.forEach { map[it.key] = it.value!! }
            return emptyList()
        }

        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<String>
            get() = String::class.java
    }

    private class TestCompactedCollectProcessor(val map: MutableMap<String, String>): CompactedProcessor<String, String> {
        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<String>
            get() = String::class.java

        override fun onSnapshot(currentData: Map<String, String>) {
            map.clear()
            map.putAll(currentData)
        }

        override fun onNext(newRecord: Record<String, String>, oldValue: String?, currentData: Map<String, String>) {
            if (newRecord.value != null) {
                map[newRecord.key] = newRecord.value!!
            } else {
                map.remove(newRecord.key)
            }
        }

    }

    @Test
    @Timeout(60)
    fun `two hosts can communicate over the p2p layer successfully`() {
        hostAConfigReadService.start()
        hostAConfigReadService.bootstrapConfig(bootstrapConfig)
        hostBConfigReadService.start()
        hostBConfigReadService.bootstrapConfig(bootstrapConfig)

        val hostALinkManager = LinkManager(hostASubscriptionFactory, hostAPublisherFactory, hostALifecycleCoordinatorFactory, hostAConfigReadService,
            SmartConfigImpl.empty(), 1, StubNetworkMap(hostALifecycleCoordinatorFactory, hostASubscriptionFactory, 1),
            ConfigBasedLinkManagerHostingMap(hostAConfigReadService, hostALifecycleCoordinatorFactory), StubCryptoService(hostALifecycleCoordinatorFactory, hostASubscriptionFactory, 1)
        )
        val hostAGateway = Gateway(hostAConfigReadService, hostASubscriptionFactory, hostAPublisherFactory, hostALifecycleCoordinatorFactory, SmartConfigImpl.empty(), 1)

        val hostBLinkManager = LinkManager(hostBSubscriptionFactory, hostBPublisherFactory, hostBLifecycleCoordinatorFactory, hostBConfigReadService,
            SmartConfigImpl.empty(), 1, StubNetworkMap(hostBLifecycleCoordinatorFactory, hostBSubscriptionFactory, 1),
            ConfigBasedLinkManagerHostingMap(hostBConfigReadService, hostBLifecycleCoordinatorFactory), StubCryptoService(hostBLifecycleCoordinatorFactory, hostBSubscriptionFactory, 1)
        )
        val hostBGateway = Gateway(hostBConfigReadService, hostBSubscriptionFactory, hostBPublisherFactory, hostBLifecycleCoordinatorFactory, SmartConfigImpl.empty(), 1)

        hostALinkManager.start()
        hostAGateway.start()
        hostBLinkManager.start()
        hostBGateway.start()

        try {

            hostAConfigWriter.updateConfiguration(
                CordaConfigurationKey(
                    "p2p-e2e-test-runner",
                    CordaConfigurationVersion("p2p", 0, 1),
                    CordaConfigurationVersion("gateway", 0, 1)
                ),
                hostAGatewayConfig
            )
            hostAConfigWriter.updateConfiguration(
                CordaConfigurationKey(
                    "p2p-e2e-test-runner",
                    CordaConfigurationVersion(LinkManagerConfiguration.PACKAGE_NAME, 0, 1),
                    CordaConfigurationVersion(LinkManagerConfiguration.COMPONENT_NAME, 0, 1)
                ),
                hostALinkManagerConfig
            )
            hostBConfigWriter.updateConfiguration(
                CordaConfigurationKey(
                    "p2p-e2e-test-runner",
                    CordaConfigurationVersion("p2p", 0, 1),
                    CordaConfigurationVersion("gateway", 0, 1)
                ),
                hostBGatewayConfig
            )
            hostBConfigWriter.updateConfiguration(
                CordaConfigurationKey(
                    "p2p-e2e-test-runner",
                    CordaConfigurationVersion(LinkManagerConfiguration.PACKAGE_NAME, 0, 1),
                    CordaConfigurationVersion(LinkManagerConfiguration.COMPONENT_NAME, 0, 1)
                ),
                hostBLinkManagerConfig
            )

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

            eventually(30.seconds) {
                assertThat(hostAGateway.isRunning).isTrue
                assertThat(hostBGateway.isRunning).isTrue
            }

            val hostAReceivedMessages = ConcurrentHashMap.newKeySet<String>()
            val hostAApplicationWriter = hostAPublisherFactory.createPublisher(PublisherConfig("app-layer", 1), bootstrapConfig)
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
            println("Messages written from application layer in host A.")

            eventually(10.seconds) {
                (1..10).forEach { messageNo ->
                    println("Received messages: $hostAReceivedMessages")
                    assertTrue(hostAReceivedMessages.contains("pong ($messageNo)"), "No reply received for message $messageNo")
                }
            }

            hostAApplicationReader.stop()
            hostBApplicationReaderWriter.stop()

        } finally {
            hostALinkManager.close()
            hostAGateway.close()
            hostBLinkManager.close()
            hostBGateway.close()
        }
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
                println("Received message: ${message.payload.array().toString(Charsets.UTF_8)}")
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
                println("Received message: ${message.payload.array().toString(Charsets.UTF_8)} and responding")
                val responseMessage = AuthenticatedMessage(
                    AuthenticatedMessageHeader(message.header.source, message.header.destination, TTL, randomId, randomId, SUBSYSTEM),
                    ByteBuffer.wrap(message.payload.array().toString(Charsets.UTF_8).replace("ping", "pong").toByteArray())
                )
                Record(Schema.P2P_OUT_TOPIC, randomId, AppMessage(responseMessage))
            }
        }

    }

}