package net.corda.p2p

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.impl.ConfigurationReadServiceImpl
import net.corda.crypto.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.KeySchemeTemplate
import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.LinkManagerProcessedMarker
import net.corda.data.p2p.markers.LinkManagerReceivedMarker
import net.corda.data.p2p.markers.TtlExpiredMarker
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
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_REFRESH_THRESHOLD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_TIMEOUT_KEY
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.LifecycleCoordinatorSchedulerFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.ENDPOINTS
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
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
import net.corda.p2p.crypto.protocol.ProtocolConstants
import net.corda.p2p.crypto.protocol.api.RevocationCheckMode
import net.corda.p2p.gateway.Gateway
import net.corda.p2p.gateway.messaging.RevocationConfig
import net.corda.p2p.gateway.messaging.RevocationConfigMode
import net.corda.p2p.gateway.messaging.SslConfiguration
import net.corda.p2p.gateway.messaging.TlsType
import net.corda.p2p.linkmanager.LinkManager
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_MARKERS
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import net.corda.test.util.eventually
import net.corda.testing.p2p.certificates.Certificates
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.seconds
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.ParameterizedSignatureSpec
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.net.URL
import java.nio.ByteBuffer
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class P2PLayerEndToEndTest {

    companion object {
        private val EXPIRED_TTL = Instant.ofEpochMilli(0)
        private const val SUBSYSTEM = "e2e.test.app"
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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

        private fun readKeyStore(resource: URL): ByteArray {
            return resource.readBytes()
        }
    }

    private val bootstrapConfig = SmartConfigFactory.createWithoutSecurityServices()
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

                eventually(20.seconds) {
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

                eventually(20.seconds) {
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
            eventually(20.seconds) {
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

                eventually(20.seconds) {
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
                    AuthenticatedMessageHeader(
                        message.header.source,
                        message.header.destination,
                        null,
                        randomId,
                        randomId,
                        SUBSYSTEM,
                        MembershipStatusFilter.ACTIVE
                    ),
                    ByteBuffer.wrap(message.payload.array().toString(Charsets.UTF_8).replace("ping", "pong").toByteArray())
                )
                Record(P2P_OUT_TOPIC, randomId, AppMessage(responseMessage))
            }
        }
    }

    private data class Identity(
        val x500Name: String,
        val groupId: String,
        val keyStoreURL: URL,
    ) {
        val name: MemberX500Name
            get() = MemberX500Name.parse(x500Name)
        val id: HoldingIdentity
            get() = HoldingIdentity(name, groupId)
    }

    private class IdentityLocalInfo(
        val identity: Identity,
        keyTemplate: KeySchemeTemplate,
        trustStoreURL: URL,
    ) {
        val keyPair by lazy {
            KeyPairGenerator.getInstance(keyTemplate.algorithmName, BouncyCastleProvider())
                .also {
                    if (keyTemplate.algSpec != null) {
                        it.initialize(keyTemplate.algSpec)
                    }
                }.genKeyPair()
        }
        val tlsKeyStore by lazy {
            KeyStore.getInstance("JKS").also { keyStore ->
                identity.keyStoreURL.openStream().use {
                    keyStore.load(it, "password".toCharArray())
                }
            }
        }
        val tlsCertificatesPem by lazy {
            tlsKeyStore.aliases()
                .toList()
                .first()
                .let { alias ->
                    val certificateChain = tlsKeyStore.getCertificateChain(alias)
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
        val groupPolicy by lazy {
            val parameters = mock<GroupPolicy.P2PParameters> {
                on { sessionPki } doReturn GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
                on { sessionTrustRoots } doReturn null
                on { protocolMode } doReturn GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode.AUTH_ENCRYPT
                on { tlsPki } doReturn GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode.STANDARD
                on { tlsTrustRoots } doReturn listOf(String(readKeyStore(trustStoreURL)))
            }

            mock<GroupPolicy> {
                on { p2pParameters } doReturn parameters
                on { groupId } doReturn identity.groupId
            }
        }

        fun createMemberInfo(endpointInfo: EndpointInfo) : MemberInfo {
            val context = mock<MemberContext> {
                on { parseList(ENDPOINTS, EndpointInfo::class.java) } doReturn listOf(endpointInfo)
                on { parse(MemberInfoExtension.GROUP_ID, String::class.java) } doReturn identity.groupId
            }
            return mock {
                on { name } doReturn identity.name
                on { memberProvidedContext } doReturn context
                on { sessionInitiationKey } doReturn keyPair.public

            }
        }
    }

    private class Host(
        ourIdentities: List<Identity>,
        p2pAddress: String,
        p2pPort: Int,
        trustStoreURL: URL,
        private val bootstrapConfig: SmartConfig,
        checkRevocation: Boolean,
        keyTemplate: KeySchemeTemplate,
    ) : AutoCloseable {
        private val endpointInfo = object: EndpointInfo {
            override val protocolVersion = ProtocolConstants.PROTOCOL_VERSION
            override val url = "https://$p2pAddress:$p2pPort$URL_PATH"
        }

        private val sslConfig = SslConfiguration(
            revocationCheck = RevocationConfig(if (checkRevocation) RevocationConfigMode.HARD_FAIL else RevocationConfigMode.OFF),
            tlsType = TlsType.ONE_WAY,
        )
        private val localInfos = ourIdentities.map { IdentityLocalInfo(it, keyTemplate, trustStoreURL) }
        private val topicService = TopicServiceImpl()
        private val lifecycleCoordinatorFactory =
            LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl(), LifecycleCoordinatorSchedulerFactoryImpl())
        private val subscriptionFactory = InMemSubscriptionFactory(topicService, RPCTopicServiceImpl(), lifecycleCoordinatorFactory)
        private val publisherFactory = CordaPublisherFactory(topicService, RPCTopicServiceImpl(), lifecycleCoordinatorFactory)
        private val configMerger = ConfigMergerImpl(DbBusConfigMergerImpl())
        private val configReadService = ConfigurationReadServiceImpl(lifecycleCoordinatorFactory, subscriptionFactory, configMerger)
        private val configPublisher = publisherFactory.createPublisher(PublisherConfig("config-writer", false), bootstrapConfig)
        private val gatewayConfig = createGatewayConfig(p2pPort, p2pAddress, sslConfig)
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

        private fun createGatewayConfig(port: Int, domainName: String, sslConfig: SslConfiguration): Config {
            return ConfigFactory.empty()
                .withValue("hostAddress", ConfigValueFactory.fromAnyRef(domainName))
                .withValue("hostPort", ConfigValueFactory.fromAnyRef(port))
                .withValue("urlPath", ConfigValueFactory.fromAnyRef(URL_PATH))
                .withValue("maxRequestSize", ConfigValueFactory.fromAnyRef(MAX_REQUEST_SIZE))
                .withValue("sslConfig.revocationCheck.mode", ConfigValueFactory.fromAnyRef(sslConfig.revocationCheck.mode.toString()))
                .withValue("sslConfig.tlsType", ConfigValueFactory.fromAnyRef(sslConfig.tlsType.toString()))
        }
        private fun Key.publicKey(): PublicKey? =
            this.toPem()
                .reader()
                .use {
                    PEMParser(it).use { parser ->
                        generateSequence {
                            parser.readObject()
                        }.map {
                            if (it is PEMKeyPair) {
                                JcaPEMKeyConverter().getKeyPair(it)
                            } else {
                                null
                            }
                        }.filterNotNull()
                            .firstOrNull()
                    }
                }?.public

        private inline fun  <reified T: Lifecycle> mockLifeCycle(stubbing: KStubbing<T>.(T) -> Unit): T {
            val name = LifecycleCoordinatorName.forComponent<T>()
            val coordinator = lifecycleCoordinatorFactory.createCoordinator(name) { _, coordinator ->
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            coordinator.start()
            return mock(stubbing = stubbing)
        }


        private val cryptoOpsClient = mockLifeCycle<CryptoOpsClient> {
            on { sign(any(), any(), any<SignatureSpec>(), any(), any()) } doAnswer {
                val tenantId: String = it.getArgument(0)
                val publicKey: PublicKey = it.getArgument(1)
                val signatureSpec: SignatureSpec = it.getArgument(2)
                val data: ByteArray = it.getArgument(3)
                val privateKeys = if(tenantId == TLS_KEY_TENANT_ID) {
                    localInfos.asSequence().flatMap { info ->
                        info.tlsKeyStore.aliases().toList().map { alias ->
                            info.tlsKeyStore.getKey(alias, "password".toCharArray())
                        }
                    }
                } else {
                    localInfos.asSequence().map { info ->
                        info.keyPair.private
                    }
                }
                val key = privateKeys.map {
                    KeyFactory.getInstance(it.algorithm, BouncyCastleProvider()).generatePrivate(
                        PKCS8EncodedKeySpec(it.encoded)
                    )
                }.firstOrNull { key ->
                    key?.publicKey() == publicKey
                } ?: throw SecurityException("Could not find key")
                val providerName = when (publicKey.algorithm) {
                    "RSA" -> "SunRsaSign"
                    "EC" -> "SunEC"
                    else -> throw SecurityException("Unsupported Algorithm")
                }
                val signature = Signature.getInstance(
                    signatureSpec.signatureName,
                    providerName
                )
                signature.initSign(key)
                (signatureSpec as? ParameterizedSignatureSpec)?.let { signature.setParameter(it.params) }
                signature.update(data)
                DigitalSignature.WithKey(publicKey, signature.sign(), emptyMap())
            }
        }
        private val groupPolicyProvider = mockLifeCycle<GroupPolicyProvider> {
            on { registerListener(any(), any()) } doAnswer {
                val callback : (HoldingIdentity, GroupPolicy) -> Unit = it.getArgument(1)
                localInfos.forEach {
                    callback.invoke(
                        it.identity.id,
                        it.groupPolicy,
                    )
                }
            }
            on { getGroupPolicy(any()) } doAnswer {
                val id: HoldingIdentity = it.getArgument(0)
                localInfos.firstOrNull {
                    it.identity.id == id
                }?.groupPolicy
            }
        }
        private val otherHostMembers = ConcurrentHashMap<MemberX500Name, MemberInfo>()
        private val otherHostMembersByKey = ConcurrentHashMap<PublicKeyHash, MemberInfo>()
        private val groupReader = mock<MembershipGroupReader> {
            on { lookup(any()) } doAnswer {
                otherHostMembers[it.getArgument(0)]
            }
            on { lookupBySessionKey(any()) } doAnswer {
                otherHostMembersByKey[it.getArgument(0)]
            }
        }
        private val membershipGroupReaderProvider = mockLifeCycle<MembershipGroupReaderProvider> {
            on { getGroupReader(any()) } doReturn groupReader
        }

        private val linkManager =
            LinkManager(
                subscriptionFactory,
                publisherFactory,
                lifecycleCoordinatorFactory,
                configReadService,
                bootstrapConfig,
                groupPolicyProvider,
                mock(),
                mock(),
                cryptoOpsClient,
                membershipGroupReaderProvider,
                mock(),
                mock(),
            )

        private val gateway =
            Gateway(
                configReadService,
                subscriptionFactory,
                publisherFactory,
                lifecycleCoordinatorFactory,
                bootstrapConfig,
                cryptoOpsClient,
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

        private fun publishNetworkMapAndIdentityKeys(otherHost: Host? = null) {
            otherHost?.localInfos?.forEach { info ->
                val identity = info.identity
                val memberInfo = info.createMemberInfo(otherHost.endpointInfo)

                val keyHash = PublicKeyHash.calculate(info.keyPair.public)

                otherHostMembers[identity.name] = memberInfo
                otherHostMembersByKey[keyHash] = memberInfo
            }

            val publisherForHost = publisherFactory.createPublisher(PublisherConfig("test-runner-publisher", false), bootstrapConfig)

            val hostingMapRecords = localInfos.map { info ->
                Record(
                    P2P_HOSTED_IDENTITIES_TOPIC, "hosting-${info.identity.name}-${info.identity.groupId}",
                    HostedIdentityEntry(
                        info.identity.id.toAvro(),
                        TLS_KEY_TENANT_ID,
                        info.tlsCertificatesPem,
                        info.keyPair.public.toPem(),
                        null
                    )
                )
            }.toList()


            publisherForHost.use { publisher ->
                publisher.start()
                publisher.publish(hostingMapRecords)
                    .forEach { it.get() }
            }
        }

        fun startWith(otherHost: Host? = null) {
            configReadService.start()
            configReadService.bootstrapConfig(bootstrapConfig)

            linkManager.start()
            gateway.start()

            publishConfig()
            publishNetworkMapAndIdentityKeys(otherHost)

            eventually(20.seconds) {
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
                    peer.id.toAvro(),
                    ourIdentity.id.toAvro(),
                    ttl,
                    incrementalId,
                    incrementalId,
                    SUBSYSTEM,
                    MembershipStatusFilter.ACTIVE
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
