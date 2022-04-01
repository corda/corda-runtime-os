package net.corda.p2p

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.impl.ConfigurationReadServiceImpl
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.libs.configuration.publish.impl.ConfigPublisherImpl
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.BASE_REPLAY_PERIOD_KEY_POSTFIX
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.CUTOFF_REPLAY_KEY_POSTFIX
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.HEARTBEAT_MESSAGE_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_MESSAGE_SIZE_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_REPLAYING_MESSAGES_PER_PEER_POSTFIX
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MESSAGE_REPLAY_KEY_PREFIX
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSIONS_PER_PEER_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_TIMEOUT_KEY
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
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
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.StubLinkManagerHostingMap
import net.corda.p2p.linkmanager.StubNetworkMap
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.markers.TtlExpiredMarker
import net.corda.p2p.test.GroupPolicyEntry
import net.corda.p2p.test.HostedIdentityEntry
import net.corda.p2p.test.KeyPairEntry
import net.corda.p2p.test.MemberInfoEntry
import net.corda.p2p.test.TenantKeys
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_MARKERS
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.TestSchema.Companion.CRYPTO_KEYS_TOPIC
import net.corda.schema.TestSchema.Companion.GROUP_POLICIES_TOPIC
import net.corda.schema.TestSchema.Companion.HOSTED_MAP_TOPIC
import net.corda.schema.TestSchema.Companion.MEMBER_INFO_TOPIC
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_SHA256_TEMPLATE
import net.corda.v5.cipher.suite.schemes.RSA_SHA256_TEMPLATE
import net.corda.v5.cipher.suite.schemes.SignatureSchemeTemplate
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.StringWriter
import java.nio.ByteBuffer
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.time.Instant
import java.util.*
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class P2PLayerEndToEndTest {

    companion object {
        private var TTL = Instant.now().toEpochMilli() + 9999999999
        private var expiredTTL = 1L
        private const val SUBSYSTEM = "e2e.test.app"
        private val logger = contextLogger()
        private const val GROUP_ID = "group-1"

        fun Key.toPem(): String {
            return StringWriter().use { str ->
                JcaPEMWriter(str).use { writer ->
                    writer.writeObject(this)
                }
                str.toString()
            }
        }
    }
    private val bootstrapConfig = SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())

    @Test
    @Timeout(60)
    fun `two hosts can exchange data messages over p2p using RSA keys`() {

        fun testMessagesBetweenTwoHosts(hostA: Host, hostB: Host) {
            hostA.startWith(hostB)
            hostB.startWith(hostA)

            val hostAReceivedMessages = ConcurrentHashMap.newKeySet<String>()
            val hostAApplicationReader = hostA.subscriptionFactory.createDurableSubscription(
                SubscriptionConfig("app-layer", P2P_IN_TOPIC, 1), InitiatorProcessor(hostAReceivedMessages),
                bootstrapConfig,
                null
            )
            val hostBApplicationReaderWriter = hostB.subscriptionFactory.createDurableSubscription(
                SubscriptionConfig("app-layer", P2P_IN_TOPIC, 1), ResponderProcessor(),
                bootstrapConfig,
                null
            )
            hostAApplicationReader.start()
            hostBApplicationReaderWriter.start()

            val hostAApplicationWriter = hostA.publisherFactory.createPublisher(PublisherConfig("app-layer", 1), bootstrapConfig)
            val initialMessages = (1..10).map { index ->
                val randomId = UUID.randomUUID().toString()
                val messageHeader = AuthenticatedMessageHeader(
                    HoldingIdentity(hostB.x500Name, GROUP_ID),
                    HoldingIdentity(hostA.x500Name, GROUP_ID),
                    TTL,
                    randomId,
                    randomId,
                    SUBSYSTEM
                )
                val message = AuthenticatedMessage(messageHeader, ByteBuffer.wrap("ping ($index)".toByteArray()))
                Record(P2P_OUT_TOPIC, randomId, AppMessage(message))
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
        }

        Host(
            "www.alice.net",
            10500,
            "O=Alice, L=London, C=GB",
            "sslkeystore_alice",
            "truststore",
            bootstrapConfig,
            true,
            RSA_SHA256_TEMPLATE,
        ).use { hostA ->
            Host(
                "chip.net",
                10501,
                "O=Chip, L=London, C=GB",
                "sslkeystore_chip",
                "truststore",
                bootstrapConfig,
                true,
                RSA_SHA256_TEMPLATE,
            ).use { hostB ->
                testMessagesBetweenTwoHosts(hostA, hostB)
            }
        }
    }

    @Test
    @Timeout(60)
    fun `two hosts can exchange data messages over p2p with ECDSA keys`() {

        fun testMessagesBetweenTwoHosts(hostA: Host, hostB: Host) {
            hostA.startWith(hostB)
            hostB.startWith(hostA)

            val hostAReceivedMessages = ConcurrentHashMap.newKeySet<String>()
            val hostAApplicationReader = hostA.subscriptionFactory.createDurableSubscription(
                SubscriptionConfig("app-layer", P2P_IN_TOPIC, 1), InitiatorProcessor(hostAReceivedMessages),
                bootstrapConfig,
                null
            )
            val hostBApplicationReaderWriter = hostB.subscriptionFactory.createDurableSubscription(
                SubscriptionConfig("app-layer", P2P_IN_TOPIC, 1), ResponderProcessor(),
                bootstrapConfig,
                null
            )
            hostAApplicationReader.start()
            hostBApplicationReaderWriter.start()

            val hostAApplicationWriter = hostA.publisherFactory.createPublisher(PublisherConfig("app-layer", 1), bootstrapConfig)
            val initialMessages = (1..10).map { index ->
                val randomId = UUID.randomUUID().toString()
                val messageHeader = AuthenticatedMessageHeader(
                    HoldingIdentity(hostB.x500Name, GROUP_ID),
                    HoldingIdentity(hostA.x500Name, GROUP_ID),
                    TTL,
                    randomId,
                    randomId,
                    SUBSYSTEM
                )
                val message = AuthenticatedMessage(messageHeader, ByteBuffer.wrap("ping ($index)".toByteArray()))
                Record(P2P_OUT_TOPIC, randomId, AppMessage(message))
            }
            hostAApplicationWriter.use {
                hostAApplicationWriter.start()
                val futures = hostAApplicationWriter.publish(initialMessages)
                futures.forEach { it.get() }
            }

            eventually(10.seconds) {
                (1..10).forEach { messageNo ->
                    assertTrue(hostAReceivedMessages.contains("pong ($messageNo)"), "No reply received for message $messageNo")
                    //TODO - check we receive a sent and received marker (put in both E2E tests)
                }
            }

            hostAApplicationReader.stop()
            hostBApplicationReaderWriter.stop()
        }

        Host(
            "www.receiver.net",
            10502,
            "O=Alice, L=London, C=GB",
            "receiver",
            "ec_truststore",
            bootstrapConfig,
            false,
            ECDSA_SECP256R1_SHA256_TEMPLATE,
        ).use { hostA ->
            Host(
                "www.sender.net",
                10503,
                "O=Bob, L=London, C=GB",
                "sender",
                "ec_truststore",
                bootstrapConfig,
                false,
                ECDSA_SECP256R1_SHA256_TEMPLATE,
            ).use { hostB ->
                testMessagesBetweenTwoHosts(hostA, hostB)
            }
        }
    }

    @Test
    @Timeout(60)
    fun `messages with expired ttl have sent marker and ttl expired marker and no received marker`() {

        fun testMessagesBetweenTwoHosts(hostA: Host, hostB: Host) {
            hostA.startWith(hostB)
            hostB.startWith(hostA)

            val hostAReceivedMessages = ConcurrentHashMap.newKeySet<String>()
            val hostAApplicationReader = hostA.subscriptionFactory.createDurableSubscription(
                SubscriptionConfig("app-layer", P2P_IN_TOPIC, 1), InitiatorProcessor(hostAReceivedMessages),
                bootstrapConfig,
                null
            )
            val hostBApplicationReaderWriter = hostB.subscriptionFactory.createDurableSubscription(
                SubscriptionConfig("app-layer", P2P_IN_TOPIC, 1), ResponderProcessor(),
                bootstrapConfig,
                null
            )

            val hostAExpiryMarkers =  Collections.synchronizedList<Record<*, *>>(mutableListOf())
            val subForP2POutMarkers = hostA.subscriptionFactory.createDurableSubscription(
                SubscriptionConfig("app-layer", P2P_OUT_MARKERS, 1), MarkerStorageProcessor(hostAExpiryMarkers),
                bootstrapConfig,
                null
            )
            hostAApplicationReader.start()
            hostBApplicationReaderWriter.start()
            subForP2POutMarkers.start()

            val hostAApplicationWriter = hostA.publisherFactory.createPublisher(PublisherConfig("app-layer", 1), bootstrapConfig)
            val initialMessages = (1..10).map { index ->
                val randomId = index.toString()
                val messageHeader = AuthenticatedMessageHeader(
                    HoldingIdentity(hostB.x500Name, GROUP_ID),
                    HoldingIdentity(hostA.x500Name, GROUP_ID),
                    expiredTTL,
                    randomId,
                    randomId,
                    SUBSYSTEM
                )
                val message = AuthenticatedMessage(messageHeader, ByteBuffer.wrap("ping ($index)".toByteArray()))
                Record(P2P_OUT_TOPIC, randomId, AppMessage(message))
            }
            hostAApplicationWriter.use {
                hostAApplicationWriter.start()
                val futures = hostAApplicationWriter.publish(initialMessages)
                futures.forEach { it.get() }
            }

            eventually(10.seconds) {
                synchronized(hostAExpiryMarkers) {
                    for ((i, msg) in initialMessages.withIndex()) {
                        assertThat(msg.key.equals(i))
                    }
                    val markers = hostAExpiryMarkers.filter{it.topic == P2P_OUT_MARKERS}.map { (it.value as AppMessageMarker).marker }
                    assertTrue(
                        ((markers.filterIsInstance<LinkManagerSentMarker>()).size == (initialMessages.size))
                    )
                    assertTrue(
                        ((markers.filterIsInstance<TtlExpiredMarker>()).size == (initialMessages.size))
                    )
                    assertTrue(
                        ((markers.filterIsInstance<LinkManagerReceivedMarker>()).isEmpty())
                    )
                }
            }

            hostAApplicationReader.stop()
            hostBApplicationReaderWriter.stop()
        }

        Host(
            "www.alice.net",
            10500,
            "O=Alice, L=London, C=GB",
            "sslkeystore_alice",
            "truststore",
            bootstrapConfig,
            true,
            KeyAlgorithm.RSA,
        ).use { hostA ->
            Host(
                "chip.net",
                10501,
                "O=Chip, L=London, C=GB",
                "sslkeystore_chip",
                "truststore",
                bootstrapConfig,
                true,
                KeyAlgorithm.RSA,
            ).use { hostB ->
                testMessagesBetweenTwoHosts(hostA, hostB)
            }
        }
    }

    private class MarkerStorageProcessor (val expiryMarkers: MutableList<Record<*, *>>): DurableProcessor<String, AppMessageMarker> {
        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<AppMessageMarker>
            get() = AppMessageMarker::class.java

        override fun onNext(events: List<Record<String, AppMessageMarker>>): List<Record<*, *>> {
            expiryMarkers.addAll(events)
            return emptyList()
        }
    }

    private class InitiatorProcessor(val receivedMessages: ConcurrentHashMap.KeySetView<String, Boolean>) : DurableProcessor<String, AppMessage> {

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

    private class ResponderProcessor : DurableProcessor<String, AppMessage> {

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
                Record(P2P_OUT_TOPIC, randomId, AppMessage(responseMessage))
            }
        }
    }

    class Host(
        p2pAddress: String,
        p2pPort: Int,
        val x500Name: String,
        private val keyStoreFileName: String,
        trustStoreFileName: String,
        private val bootstrapConfig: SmartConfig,
        checkRevocation: Boolean,
        signatureTemplate: SignatureSchemeTemplate,
    ) : AutoCloseable {

        private val sslConfig = SslConfiguration(
            revocationCheck = RevocationConfig(if (checkRevocation) RevocationConfigMode.HARD_FAIL else RevocationConfigMode.OFF)
        )
        val keyPair = KeyPairGenerator.getInstance(signatureTemplate.algorithmName, BouncyCastleProvider())
            .also {
                if (signatureTemplate.algSpec != null) {
                    it.initialize(signatureTemplate.algSpec)
                }
            }
            .genKeyPair()
        val topicService = TopicServiceImpl()
        val lifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
        val subscriptionFactory = InMemSubscriptionFactory(topicService, RPCTopicServiceImpl(), lifecycleCoordinatorFactory)
        val publisherFactory = CordaPublisherFactory(topicService, RPCTopicServiceImpl(), lifecycleCoordinatorFactory)
        val configReadService = ConfigurationReadServiceImpl(lifecycleCoordinatorFactory, subscriptionFactory)
        val configPublisher = ConfigPublisherImpl(
            CONFIG_TOPIC,
            publisherFactory.createPublisher(PublisherConfig("config-writer"))
        )
        val gatewayConfig = createGatewayConfig(p2pPort, p2pAddress, sslConfig)
        val tlsTenantId by lazy {
            GROUP_ID
        }
        val sessionKeyTenantId by lazy {
            x500Name
        }
        val linkManagerConfig by lazy {
            ConfigFactory.empty()
                .withValue(MAX_MESSAGE_SIZE_KEY, ConfigValueFactory.fromAnyRef(1000000))
                .withValue("$MESSAGE_REPLAY_KEY_PREFIX$BASE_REPLAY_PERIOD_KEY_POSTFIX", ConfigValueFactory.fromAnyRef(2000))
                .withValue("$MESSAGE_REPLAY_KEY_PREFIX$CUTOFF_REPLAY_KEY_POSTFIX", ConfigValueFactory.fromAnyRef(10000))
                .withValue("$MESSAGE_REPLAY_KEY_PREFIX$MAX_REPLAYING_MESSAGES_PER_PEER_POSTFIX", ConfigValueFactory.fromAnyRef(100))
                .withValue(HEARTBEAT_MESSAGE_PERIOD_KEY, ConfigValueFactory.fromAnyRef(2000))
                .withValue(SESSION_TIMEOUT_KEY, ConfigValueFactory.fromAnyRef(10000))
                .withValue(SESSIONS_PER_PEER_KEY, ConfigValueFactory.fromAnyRef(4))
        }

        private fun readKeyStore(fileName: String): ByteArray {
            return javaClass.classLoader.getResource(fileName).readBytes()
        }

        private fun createGatewayConfig(port: Int, domainName: String, sslConfig: SslConfiguration): Config {
            return ConfigFactory.empty()
                .withValue("hostAddress", ConfigValueFactory.fromAnyRef(domainName))
                .withValue("hostPort", ConfigValueFactory.fromAnyRef(port))
                .withValue("sslConfig.revocationCheck.mode", ConfigValueFactory.fromAnyRef(sslConfig.revocationCheck.mode.toString()))
        }

        val linkManager =
            LinkManager(
                subscriptionFactory,
                publisherFactory,
                lifecycleCoordinatorFactory,
                configReadService,
                bootstrapConfig,
                1,
            )

        val gateway =
            Gateway(
                configReadService,
                subscriptionFactory,
                publisherFactory,
                lifecycleCoordinatorFactory,
                bootstrapConfig,
                1,
            )

        private fun publishGatewayConfig() {
            configPublisher.updateConfiguration(
                CordaConfigurationKey(
                    "p2p-e2e-test-runner",
                    CordaConfigurationVersion("p2p", 0, 1),
                    CordaConfigurationVersion("gateway", 0, 1)
                ),
                gatewayConfig
            )
        }

        private fun publishLinkManagerConfig() {
            configPublisher.updateConfiguration(
                CordaConfigurationKey(
                    "p2p-e2e-test-runner",
                    CordaConfigurationVersion(LinkManagerConfiguration.PACKAGE_NAME, 0, 1),
                    CordaConfigurationVersion(LinkManagerConfiguration.COMPONENT_NAME, 0, 1)
                ),
                linkManagerConfig
            )
        }

        private fun publishConfig() {
            publishGatewayConfig()
            publishLinkManagerConfig()
        }

        private val keyStore = KeyStore.getInstance("JKS").also { keyStore ->
            javaClass.classLoader.getResource("$keyStoreFileName.jks")!!.openStream().use {
                keyStore.load(it, "password".toCharArray())
            }
        }
        private val tlsCertificatesPem = keyStore.aliases()
            .toList()
            .first()
            .let { alias ->
                val certificateChain = keyStore.getCertificateChain(alias)
                certificateChain.map { certificate ->
                    StringWriter().use { str ->
                        JcaPEMWriter(str).use { writer ->
                            writer.writeObject(certificate)
                        }
                        str.toString()
                    }
                }
            }
        private val groupPolicyEntry = GroupPolicyEntry(
            GROUP_ID,
            NetworkType.CORDA_5,
            listOf(
                ProtocolMode.AUTHENTICATION_ONLY,
                ProtocolMode.AUTHENTICATED_ENCRYPTION,
            ),
            listOf(String(readKeyStore("$trustStoreFileName.pem"))),
        )

        private val memberInfoEntry =
            MemberInfoEntry(
                HoldingIdentity(x500Name, GROUP_ID),
                keyPair.public.toPem(),
                "http://$p2pAddress:$p2pPort",
            )

        private fun publishNetworkMapAndIdentityKeys(otherHost: Host) {
            val publisherForHost = publisherFactory.createPublisher(PublisherConfig("test-runner-publisher", 1), bootstrapConfig)
            val networkMapEntries = mapOf(
                "$x500Name-$GROUP_ID" to memberInfoEntry,
                "${otherHost.x500Name}-$GROUP_ID" to otherHost.memberInfoEntry,
            )
            val networkMapRecords = networkMapEntries.map { Record(MEMBER_INFO_TOPIC, it.key, it.value) }
            val HostedIdentityEntry = HostedIdentityEntry(
                HoldingIdentity(x500Name, GROUP_ID),
                GROUP_ID,
                x500Name,
                tlsCertificatesPem,
                keyPair.public.toPem(),
            )
            publisherForHost.use { publisher ->
                publisher.start()
                publisher.publish(networkMapRecords).forEach { it.get() }
                publisher.publish(
                    listOf(
                        Record(
                            CRYPTO_KEYS_TOPIC,
                            "key-1",
                            TenantKeys(
                                sessionKeyTenantId,
                                KeyPairEntry(
                                    keyPair.private.toPem()
                                )
                            )
                        ),
                        Record(
                            GROUP_POLICIES_TOPIC,
                            GROUP_ID,
                            groupPolicyEntry,
                        ),
                        Record(
                            HOSTED_MAP_TOPIC,
                            "hosting-1",
                            HostedIdentityEntry,
                        )
                    )
                ).forEach { it.get() }
            }
        }

        fun publishTlsKeys() {
            val records = keyStore.aliases().toList().map { alias ->
                val privateKey = keyStore.getKey(alias, "password".toCharArray()).let {
                    KeyFactory.getInstance(it.algorithm, BouncyCastleProvider()).generatePrivate(
                        PKCS8EncodedKeySpec(it.encoded))
                }

                val keyPair = KeyPairEntry(
                    privateKey.toPem()
                )
                Record(
                    CRYPTO_KEYS_TOPIC,
                    alias,
                    TenantKeys(
                        tlsTenantId,
                        keyPair,
                    )
                )
            }
            publisherFactory.createPublisher(
                PublisherConfig(
                    "test-runner-publisher",
                    1
                ),
                bootstrapConfig
            ).use { publisher ->
                publisher.start()
                publisher.publish(records).forEach { it.get() }
            }
        }

        fun startWith(otherHost: Host) {
            configReadService.start()
            configReadService.bootstrapConfig(bootstrapConfig)
            publishTlsKeys()

            linkManager.start()
            gateway.start()

            publishConfig()
            publishNetworkMapAndIdentityKeys(otherHost)

            eventually(30.seconds) {
                assertThat(linkManager.isRunning).isTrue
                assertThat(gateway.isRunning).isTrue
            }
        }

        override fun close() {
            linkManager.close()
            gateway.close()
        }
    }
}
