package net.corda.p2p

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.impl.ConfigurationReadServiceImpl
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.merger.impl.ConfigMergerImpl
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.HEARTBEAT_MESSAGE_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_MESSAGE_SIZE_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_REPLAYING_MESSAGES_PER_PEER
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MESSAGE_REPLAY_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.REPLAY_ALGORITHM_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.REVOCATION_CHECK_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSIONS_PER_PEER_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_TIMEOUT_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_REFRESH_THRESHOLD_KEY
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.LifecycleCoordinatorSchedulerFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messagebus.db.configuration.DbBusConfigMergerImpl
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.RevocationCheckMode
import net.corda.p2p.gateway.Gateway
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SigningMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.common.ThirdPartyComponentsMode
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.markers.LinkManagerProcessedMarker
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.TtlExpiredMarker
import net.corda.p2p.test.GroupPolicyEntry
import net.corda.p2p.test.KeyPairEntry
import net.corda.p2p.test.MemberInfoEntry
import net.corda.p2p.test.TenantKeys
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.P2P.Companion.CRYPTO_KEYS_TOPIC
import net.corda.schema.Schemas.P2P.Companion.GROUP_POLICIES_TOPIC
import net.corda.schema.Schemas.P2P.Companion.MEMBER_INFO_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_MARKERS
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import net.corda.test.util.eventually
import net.corda.testing.p2p.certificates.Certificates
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import net.corda.v5.base.util.toHex
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.v5.cipher.suite.schemes.KeySchemeTemplate
import net.corda.v5.cipher.suite.schemes.RSA_TEMPLATE
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.mock
import java.io.StringWriter
import java.net.URL
import java.nio.ByteBuffer
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

class P2PLayerEndToEndTest {

    companion object {
        private val EXPIRED_TTL = Instant.ofEpochMilli(0)
        private const val SUBSYSTEM = "e2e.test.app"
        private val logger = contextLogger()
        private const val GROUP_ID = "group-1"
        private const val TLS_KEY_TENANT_ID = "p2p"
        private const val URL_PATH = "/gateway"

        private const val MAX_REQUEST_SIZE = 50_000_000L

        fun Key.toPem(): String {
            return StringWriter().use { str ->
                JcaPEMWriter(str).use { writer ->
                    writer.writeObject(this)
                }
                str.toString()
            }
        }
    }

    private val bootstrapConfig = SmartConfigFactory.create(ConfigFactory.empty())
        .create(ConfigFactory.empty().withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1)))

    @Test
    @Timeout(60)
    fun `two hosts can exchange data messages over p2p using RSA keys`() {
        val numberOfMessages = 10
        val aliceId = Identity("O=Alice, L=London, C=GB", GROUP_ID, Certificates.aliceKeyStoreFile)
        val chipId = Identity("O=Chip, L=London, C=GB", GROUP_ID, Certificates.chipKeyStoreFile)
        Host(
            listOf(aliceId),
            "www.alice.net",
            10500,
            Certificates.truststoreCertificatePem,
            bootstrapConfig,
            true,
            RSA_TEMPLATE,
        ).use { hostA ->
            Host(
                listOf(chipId),
                "chip.net",
                10501,
                Certificates.truststoreCertificatePem,
                bootstrapConfig,
                true,
                RSA_TEMPLATE,
            ).use { hostB ->
                hostA.startWith(hostB)
                hostB.startWith(hostA)

                val hostAReceivedMessages = ConcurrentHashMap.newKeySet<String>()
                val hostAApplicationReader = hostA.listenForReceivedMessages(hostAReceivedMessages)
                val hostBApplicationReaderWriter = hostB.addReadWriter()
                val hostAMarkers = CopyOnWriteArrayList<Record<String, AppMessageMarker>>()
                val hostAMarkerReader = hostA.listenForMarkers(hostAMarkers)
                hostA.sendMessages(numberOfMessages, aliceId, chipId)

                eventually(10.seconds) {
                    val messagesWithProcessedMarker = hostAMarkers.filter { it.value!!.marker is LinkManagerProcessedMarker }
                        .map { it.key }.toSet()
                    val messagesWithReceivedMarker = hostAMarkers.filter { it.value!!.marker is LinkManagerReceivedMarker }
                        .map { it.key }.toSet()

                    assertThat(messagesWithProcessedMarker).containsExactlyInAnyOrderElementsOf((1..numberOfMessages).map { it.toString() })
                    assertThat(messagesWithReceivedMarker).containsExactlyInAnyOrderElementsOf((1..numberOfMessages).map { it.toString() })
                    assertThat(hostAReceivedMessages).containsExactlyInAnyOrderElementsOf((1..numberOfMessages).map { "pong ($it)" })
                }

                hostAApplicationReader.close()
                hostBApplicationReaderWriter.close()
                hostAMarkerReader.close()
            }
        }
    }

    @Test
    @Timeout(60)
    fun `two hosts can exchange data messages over p2p with ECDSA keys`() {
        val numberOfMessages = 10
        val receiverId = Identity("O=Alice, L=London, C=GB", GROUP_ID, Certificates.receiverKeyStoreFile)
        val senderId = Identity("O=Chip, L=London, C=GB", GROUP_ID, Certificates.senderKeyStoreFile)
        Host(
            listOf(receiverId),
            "www.receiver.net",
            10502,
            Certificates.ecTrustStorePem,
            bootstrapConfig,
            false,
            ECDSA_SECP256R1_TEMPLATE,
        ).use { hostA ->
            Host(
                listOf(senderId),
                "www.sender.net",
                10503,
                Certificates.ecTrustStorePem,
                bootstrapConfig,
                false,
                ECDSA_SECP256R1_TEMPLATE,
            ).use { hostB ->
                hostA.startWith(hostB)
                hostB.startWith(hostA)

                val hostAReceivedMessages = ConcurrentHashMap.newKeySet<String>()
                val hostAApplicationReader = hostA.listenForReceivedMessages(hostAReceivedMessages)
                val hostBApplicationReaderWriter = hostB.addReadWriter()
                val hostAMarkers = CopyOnWriteArrayList<Record<String, AppMessageMarker>>()
                val hostAMarkerReader = hostA.listenForMarkers(hostAMarkers)
                hostA.sendMessages(numberOfMessages, receiverId, senderId)

                eventually(10.seconds) {
                    val messagesWithProcessedMarker = hostAMarkers.filter { it.value!!.marker is LinkManagerProcessedMarker }
                        .map { it.key }.toSet()
                    val messagesWithReceivedMarker = hostAMarkers.filter { it.value!!.marker is LinkManagerReceivedMarker }
                        .map { it.key }.toSet()

                    assertThat(messagesWithProcessedMarker).containsExactlyInAnyOrderElementsOf((1..numberOfMessages).map { it.toString() })
                    assertThat(messagesWithReceivedMarker).containsExactlyInAnyOrderElementsOf((1..numberOfMessages).map { it.toString() })
                    assertThat(hostAReceivedMessages).containsExactlyInAnyOrderElementsOf((1..numberOfMessages).map { "pong ($it)" })
                }
                hostAApplicationReader.close()
                hostBApplicationReaderWriter.close()
                hostAMarkerReader.close()
            }
        }
    }

    @Test
    @Timeout(60)
    fun `messages can be looped back between locally hosted identities`() {
        val numberOfMessages = 10
        val receiverId = Identity("O=Alice, L=London, C=GB", GROUP_ID, Certificates.receiverKeyStoreFile)
        val senderId = Identity("O=Chip, L=London, C=GB", GROUP_ID, Certificates.senderKeyStoreFile)
        Host(
            listOf(receiverId, senderId),
            "www.alice.net",
            10500,
            Certificates.truststoreCertificatePem,
            bootstrapConfig,
            true,
            RSA_TEMPLATE,
        ).use { host ->
            host.startWith()
            val hostReceivedMessages = ConcurrentHashMap.newKeySet<String>()
            val hostApplicationReader = host.listenForReceivedMessages(hostReceivedMessages)
            val hostMarkers = CopyOnWriteArrayList<Record<String, AppMessageMarker>>()
            val hostMarkerReader = host.listenForMarkers(hostMarkers)

            host.sendMessages(numberOfMessages, receiverId, senderId)
            eventually(10.seconds) {
                val messagesWithProcessedMarker = hostMarkers.filter { it.value!!.marker is LinkManagerProcessedMarker }.map { it.key }.toSet()
                val messagesWithReceivedMarker = hostMarkers.filter { it.value!!.marker is LinkManagerReceivedMarker }.map { it.key }.toSet()
                assertThat(messagesWithProcessedMarker).containsExactlyInAnyOrderElementsOf((1..numberOfMessages).map { it.toString() })
                assertThat(messagesWithReceivedMarker).containsExactlyInAnyOrderElementsOf((1..numberOfMessages).map { it.toString() })
                assertThat(hostReceivedMessages).containsExactlyInAnyOrderElementsOf((1..numberOfMessages).map { "ping ($it)" })
            }
            hostApplicationReader.close()
            hostMarkerReader.close()
        }
    }

    @Test
    @Timeout(60)
    fun `messages with expired ttl have processed marker and ttl expired marker and no received marker`() {
        val numberOfMessages = 10
        val aliceId = Identity("O=Alice, L=London, C=GB", GROUP_ID, Certificates.aliceKeyStoreFile)
        val chipId = Identity("O=Chip, L=London, C=GB", GROUP_ID, Certificates.chipKeyStoreFile)
        Host(
            listOf(aliceId),
            "www.alice.net",
            10500,
            Certificates.truststoreCertificatePem,
            bootstrapConfig,
            true,
            RSA_TEMPLATE,
        ).use { hostA ->
            Host(
                listOf(chipId),
                "chip.net",
                10501,
                Certificates.truststoreCertificatePem,
                bootstrapConfig,
                true,
                RSA_TEMPLATE,
            ).use { hostB ->
                hostA.startWith(hostB)
                hostB.startWith(hostA)

                val hostAReceivedMessages = ConcurrentHashMap.newKeySet<String>()
                val hostAApplicationReader = hostA.listenForReceivedMessages(hostAReceivedMessages)
                val hostBApplicationReaderWriter = hostB.addReadWriter()
                val hostAMarkers = CopyOnWriteArrayList<Record<String, AppMessageMarker>>()
                val hostAMarkerReader = hostA.listenForMarkers(hostAMarkers)
                hostA.sendMessages(numberOfMessages, aliceId, chipId, EXPIRED_TTL)

                eventually(10.seconds) {
                    val markers = hostAMarkers.filter { it.topic == P2P_OUT_MARKERS }.map { (it.value as AppMessageMarker).marker }
                    assertThat(hostAMarkers.filter { it.value!!.marker is LinkManagerProcessedMarker }.map { it.key })
                        .containsExactlyInAnyOrderElementsOf((1..numberOfMessages).map { it.toString() })
                    assertThat(hostAMarkers.filter { it.value!!.marker is TtlExpiredMarker }.map { it.key })
                        .containsExactlyInAnyOrderElementsOf((1..numberOfMessages).map { it.toString() })
                    assertThat(markers.filterIsInstance<LinkManagerReceivedMarker>()).isEmpty()
                }
                hostAApplicationReader.close()
                hostBApplicationReaderWriter.close()
                hostAMarkerReader.close()
            }
        }
    }


    private class MarkerStorageProcessor(val markers: MutableCollection<Record<String, AppMessageMarker>>) :
        DurableProcessor<String, AppMessageMarker> {
        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<AppMessageMarker>
            get() = AppMessageMarker::class.java

        override fun onNext(events: List<Record<String, AppMessageMarker>>): List<Record<*, *>> {
            markers.addAll(events)
            return emptyList()
        }
    }

    private class InitiatorProcessor(val receivedMessages: MutableCollection<String>) : DurableProcessor<String, AppMessage> {

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
                    AuthenticatedMessageHeader(message.header.source, message.header.destination, null, randomId, randomId, SUBSYSTEM),
                    ByteBuffer.wrap(message.payload.array().toString(Charsets.UTF_8).replace("ping", "pong").toByteArray())
                )
                Record(P2P_OUT_TOPIC, randomId, AppMessage(responseMessage))
            }
        }
    }

    internal data class Identity(
        val x500Name: String,
        val groupId: String,
        val keyStoreURL: URL,
    )

    internal class Host(
        private val ourIdentities: List<Identity>,
        p2pAddress: String,
        p2pPort: Int,
        trustStoreURL: URL,
        private val bootstrapConfig: SmartConfig,
        checkRevocation: Boolean,
        keyTemplate: KeySchemeTemplate,
    ) : AutoCloseable {

        private val sslConfig = SslConfiguration(
            revocationCheck = RevocationConfig(if (checkRevocation) RevocationConfigMode.HARD_FAIL else RevocationConfigMode.OFF)
        )
        private val keyPairs = ourIdentities.map {
            KeyPairGenerator.getInstance(keyTemplate.algorithmName, BouncyCastleProvider())
                .also {
                    if (keyTemplate.algSpec != null) {
                        it.initialize(keyTemplate.algSpec)
                    }
                }.genKeyPair()
        }
        private val topicService = TopicServiceImpl()
        private val lifecycleCoordinatorFactory =
            LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl(), LifecycleCoordinatorSchedulerFactoryImpl())
        private val subscriptionFactory = InMemSubscriptionFactory(topicService, RPCTopicServiceImpl(), lifecycleCoordinatorFactory)
        private val publisherFactory = CordaPublisherFactory(topicService, RPCTopicServiceImpl(), lifecycleCoordinatorFactory)
        private val configMerger = ConfigMergerImpl(DbBusConfigMergerImpl())
        private val configReadService = ConfigurationReadServiceImpl(lifecycleCoordinatorFactory, subscriptionFactory, configMerger)
        private val configPublisher = publisherFactory.createPublisher(PublisherConfig("config-writer", false), bootstrapConfig)
        private val gatewayConfig = createGatewayConfig(p2pPort, p2pAddress, sslConfig)
        private val tlsTenantId by lazy {
            TLS_KEY_TENANT_ID
        }
        private val linkManagerConfig by lazy {
            ConfigFactory.empty()
                .withValue(MAX_MESSAGE_SIZE_KEY, ConfigValueFactory.fromAnyRef(1000000))
                .withValue(MAX_REPLAYING_MESSAGES_PER_PEER, ConfigValueFactory.fromAnyRef(100))
                .withValue(HEARTBEAT_MESSAGE_PERIOD_KEY, ConfigValueFactory.fromAnyRef(Duration.ofSeconds(2)))
                .withValue(SESSION_TIMEOUT_KEY, ConfigValueFactory.fromAnyRef(Duration.ofSeconds(10)))
                .withValue(SESSIONS_PER_PEER_KEY, ConfigValueFactory.fromAnyRef(4))
                .withValue(SESSION_REFRESH_THRESHOLD_KEY, ConfigValueFactory.fromAnyRef(432000))
                .withValue(
                    REPLAY_ALGORITHM_KEY,
                    ConfigFactory.empty().withValue(
                        LinkManagerConfiguration.ReplayAlgorithm.Constant.configKeyName(),
                        replayConfig.root()
                    ).root())
                .withValue(REVOCATION_CHECK_KEY, ConfigValueFactory.fromAnyRef(RevocationCheckMode.OFF.toString()))
        }
        private val replayConfig by lazy {
            ConfigFactory.empty()
                .withValue(MESSAGE_REPLAY_PERIOD_KEY, ConfigValueFactory.fromAnyRef(Duration.ofSeconds(2)))
        }

        private fun readKeyStore(resource: URL): ByteArray {
            return resource.readBytes()
        }

        private fun createGatewayConfig(port: Int, domainName: String, sslConfig: SslConfiguration): Config {
            return ConfigFactory.empty()
                .withValue("hostAddress", ConfigValueFactory.fromAnyRef(domainName))
                .withValue("hostPort", ConfigValueFactory.fromAnyRef(port))
                .withValue("urlPath", ConfigValueFactory.fromAnyRef(URL_PATH))
                .withValue("maxRequestSize", ConfigValueFactory.fromAnyRef(MAX_REQUEST_SIZE))
                .withValue("sslConfig.revocationCheck.mode", ConfigValueFactory.fromAnyRef(sslConfig.revocationCheck.mode.toString()))
        }

        private val linkManager =
            LinkManager(
                subscriptionFactory,
                publisherFactory,
                lifecycleCoordinatorFactory,
                configReadService,
                bootstrapConfig,
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                ThirdPartyComponentsMode.STUB
            )

        private val gateway =
            Gateway(
                configReadService,
                subscriptionFactory,
                publisherFactory,
                lifecycleCoordinatorFactory,
                bootstrapConfig,
                SigningMode.STUB,
                mock(),
                AvroSchemaRegistryImpl()
            )

        private fun Publisher.publishConfig(key: String, config: Config) {
            val configSource = config.root().render(ConfigRenderOptions.concise())
            this.publish(
                listOf(
                    Record(
                        CONFIG_TOPIC,
                        key,
                        Configuration(configSource, configSource, 0, ConfigurationSchemaVersion(1, 0))
                    )
                )
            ).forEach { it.get() }
        }

        private fun publishConfig() {
            configPublisher.publishConfig(ConfigKeys.P2P_GATEWAY_CONFIG, gatewayConfig)
            configPublisher.publishConfig(ConfigKeys.P2P_LINK_MANAGER_CONFIG, linkManagerConfig)
        }

        private val keyStores = ourIdentities.mapNotNull {
            KeyStore.getInstance("JKS").also { keyStore ->
                it.keyStoreURL.openStream().use {
                    keyStore.load(it, "password".toCharArray())
                }
            }
        }
        private val tlsCertificatesPem = keyStores.map {
            it.aliases()
                .toList()
                .first()
                .let { alias ->
                    val certificateChain = it.getCertificateChain(alias)
                    certificateChain.map { certificate ->
                        StringWriter().use { str ->
                            JcaPEMWriter(str).use { writer ->
                                writer.writeObject(certificate)
                            }
                            str.toString()
                        }
                    }
                }
        }
        private val groupPolicyEntry = ourIdentities.map {
            GroupPolicyEntry(
                HoldingIdentity(it.x500Name, it.groupId),
                NetworkType.CORDA_5,
                listOf(
                    ProtocolMode.AUTHENTICATION_ONLY,
                    ProtocolMode.AUTHENTICATED_ENCRYPTION,
                ),
                listOf(String(readKeyStore(trustStoreURL))),
            )
        }



        private val memberInfoEntry = ourIdentities.mapIndexed { i, identity ->
            MemberInfoEntry(
                HoldingIdentity(identity.x500Name, identity.groupId),
                keyPairs[i].public.toPem(),
                "http://$p2pAddress:$p2pPort$URL_PATH",
            )
        }

        private fun publishNetworkMapAndIdentityKeys(otherHost: Host? = null) {
            val publisherForHost = publisherFactory.createPublisher(PublisherConfig("test-runner-publisher", false), bootstrapConfig)
            val memberInfoRecords = ourIdentities.mapIndexed { i, identity ->
                Record(MEMBER_INFO_TOPIC, "${identity.x500Name}-${identity.groupId}", memberInfoEntry[i])
            }
            val otherHostMemberInfoRecords = otherHost?.ourIdentities?.mapIndexed { i, identity ->
                Record(MEMBER_INFO_TOPIC, "${identity.x500Name}-${identity.groupId}", otherHost.memberInfoEntry[i])
            }?.toList() ?: emptyList()

            val hostingMapRecords = ourIdentities.mapIndexed { i, identity ->
                Record(
                    P2P_HOSTED_IDENTITIES_TOPIC, "hosting-$i",
                    HostedIdentityEntry(
                        HoldingIdentity(identity.x500Name, identity.groupId),
                        TLS_KEY_TENANT_ID,
                        identity.x500Name,
                        tlsCertificatesPem[i],
                        keyPairs[i].public.toPem(),
                        null
                    )
                )
            }.toList()

            val groupPolicyRecord = groupPolicyEntry.map {
                Record(
                    GROUP_POLICIES_TOPIC,
                    "${it.holdingIdentity.x500Name}-${it.holdingIdentity.groupId}",
                    it,
                )
            }

            val cryptoKeyRecords = ourIdentities.mapIndexed { i, identity ->
                Record(CRYPTO_KEYS_TOPIC, "key-1", TenantKeys(identity.x500Name, KeyPairEntry(keyPairs[i].private.toPem())))
            }.toList()

            publisherForHost.use { publisher ->
                publisher.start()
                publisher.publish(
                    memberInfoRecords + otherHostMemberInfoRecords +
                            hostingMapRecords + cryptoKeyRecords + groupPolicyRecord
                ).forEach { it.get() }
            }
        }

        private fun publishTlsKeys() {
            val records = keyStores.flatMap { keyStore ->
                keyStore.aliases().toList().map { alias ->
                    val privateKey = keyStore.getKey(alias, "password".toCharArray()).let {
                        KeyFactory.getInstance(it.algorithm, BouncyCastleProvider()).generatePrivate(
                            PKCS8EncodedKeySpec(it.encoded)
                        )
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
                }.toList()
            }
            publisherFactory.createPublisher(
                PublisherConfig("test-runner-publisher", false),
                bootstrapConfig
            ).use { publisher ->
                publisher.start()
                publisher.publish(records).forEach { it.get() }
            }
        }

        fun startWith(otherHost: Host? = null) {
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

        fun addReadWriter(): Subscription<String, AppMessage> {
            return subscriptionFactory.createDurableSubscription(
                SubscriptionConfig("app-layer", P2P_IN_TOPIC), ResponderProcessor(),
                bootstrapConfig,
                null
            ).also { it.start() }
        }

        fun listenForReceivedMessages(
            receivedMessages: MutableCollection<String>
        ): Subscription<String, AppMessage> {
            return subscriptionFactory.createDurableSubscription(
                SubscriptionConfig("app-layer", P2P_IN_TOPIC), InitiatorProcessor(receivedMessages),
                bootstrapConfig,
                null
            ).also { it.start() }
        }

        fun listenForMarkers(markers: MutableCollection<Record<String, AppMessageMarker>>): Subscription<String, AppMessageMarker> {
            return subscriptionFactory.createDurableSubscription(
                SubscriptionConfig("app-layer", P2P_OUT_MARKERS), MarkerStorageProcessor(markers),
                bootstrapConfig,
                null
            ).also { it.start() }
        }

        fun sendMessages(messagesToSend: Int, ourIdentity: Identity, peer: Identity, ttl: Instant? = null) {
            val hostAApplicationWriter = publisherFactory.createPublisher(PublisherConfig("app-layer", false), bootstrapConfig)
            val initialMessages = (1..messagesToSend).map { index ->
                val incrementalId = index.toString()
                val messageHeader = AuthenticatedMessageHeader(
                    HoldingIdentity(peer.x500Name, peer.groupId),
                    HoldingIdentity(ourIdentity.x500Name, ourIdentity.groupId),
                    ttl,
                    incrementalId,
                    incrementalId,
                    SUBSYSTEM
                )
                val message = AuthenticatedMessage(messageHeader, ByteBuffer.wrap("ping ($index)".toByteArray()))
                Record(P2P_OUT_TOPIC, incrementalId, AppMessage(message))
            }
            hostAApplicationWriter.use {
                hostAApplicationWriter.start()
                val futures = hostAApplicationWriter.publish(initialMessages)
                futures.forEach { it.get() }
            }
        }
    }
}
