package net.corda.membership.impl.synchronisation

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.chunking.toAvro
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.hes.StableKeyPairDecryptor
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.crypto.SecureHash
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.GroupParameters
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedMemberInfo
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.MembershipSyncRequest
import net.corda.data.membership.p2p.SignedMemberships
import net.corda.data.membership.p2p.WireGroupParameters
import net.corda.data.sync.BloomFilter
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.layeredpropertymap.toAvro
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.synchronisation.dummy.MemberTestGroupPolicy
import net.corda.membership.impl.synchronisation.dummy.MgmTestGroupPolicy
import net.corda.membership.impl.synchronisation.dummy.TestCryptoOpsClient
import net.corda.membership.impl.synchronisation.dummy.TestGroupPolicyProvider
import net.corda.membership.impl.synchronisation.dummy.TestGroupReaderProvider
import net.corda.membership.impl.synchronisation.dummy.TestMembershipPersistenceClient
import net.corda.membership.impl.synchronisation.dummy.TestMembershipQueryClient
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.toSortedMap
import net.corda.membership.lib.toWire
import net.corda.membership.p2p.MembershipP2PReadService
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.Verifier
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.synchronisation.SynchronisationProxy
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.Membership.Companion.GROUP_PARAMETERS_TOPIC
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MEMBERSHIP_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MembershipConfig.MAX_DURATION_BETWEEN_SYNC_REQUESTS_MINUTES
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.MEMBERS_PACKAGE_UPDATE
import net.corda.schema.configuration.MembershipConfig.TtlsConfig.TTLS
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.test.util.eventually
import net.corda.test.util.time.TestClock
import net.corda.utilities.concurrent.getOrThrow
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CompletableFuture

@ExtendWith(ServiceExtension::class, DBSetup::class)
class SynchronisationIntegrationTest {
    private companion object {
        @InjectService(timeout = 5000)
        lateinit var publisherFactory: PublisherFactory

        @InjectService(timeout = 5000)
        lateinit var subscriptionFactory: SubscriptionFactory

        @InjectService(timeout = 5000)
        lateinit var configurationReadService: ConfigurationReadService

        @InjectService(timeout = 5000)
        lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

        @InjectService(timeout = 5000)
        lateinit var synchronisationProxy: SynchronisationProxy

        @InjectService(timeout = 5000)
        lateinit var groupPolicyProvider: TestGroupPolicyProvider

        @InjectService(timeout = 5000)
        lateinit var keyEncodingService: KeyEncodingService

        @InjectService(timeout = 5000)
        lateinit var membershipGroupReaderProvider: TestGroupReaderProvider

        @InjectService(timeout = 5000)
        lateinit var membershipP2PReadService: MembershipP2PReadService

        @InjectService(timeout = 5000)
        lateinit var merkleTreeProvider: MerkleTreeProvider

        @InjectService(timeout = 5000)
        lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

        @InjectService(timeout = 5000)
        lateinit var cryptoOpsClient: TestCryptoOpsClient

        @InjectService(timeout = 5000)
        lateinit var membershipQueryClient: TestMembershipQueryClient

        @InjectService(timeout = 5000)
        lateinit var membershipPersistenceClient: TestMembershipPersistenceClient

        @InjectService(timeout = 5000)
        lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService

        @InjectService(timeout = 5000)
        lateinit var memberInfoFactory: MemberInfoFactory

        @InjectService(timeout = 5000)
        lateinit var stableKeyPairDecryptor: StableKeyPairDecryptor

        @InjectService(timeout = 5000)
        lateinit var groupParametersWriterService: GroupParametersWriterService

        val merkleTreeGenerator: MerkleTreeGenerator by lazy {
            MerkleTreeGenerator(
                merkleTreeProvider,
                cordaAvroSerializationFactory
            )
        }

        val keyValueSerializer: CordaAvroSerializer<KeyValuePairList> by lazy {
            cordaAvroSerializationFactory.createAvroSerializer { }
        }
        val membershipPackageSerializer: CordaAvroSerializer<MembershipPackage> by lazy {
            cordaAvroSerializationFactory.createAvroSerializer { }
        }
        val syncRequestSerializer: CordaAvroSerializer<MembershipSyncRequest> by lazy {
            cordaAvroSerializationFactory.createAvroSerializer { }
        }
        val membershipPackageDeserializer: CordaAvroDeserializer<MembershipPackage> by lazy {
            cordaAvroSerializationFactory.createAvroDeserializer({}, MembershipPackage::class.java)
        }
        val keyValueDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy{
            cordaAvroSerializationFactory.createAvroDeserializer({}, KeyValuePairList::class.java)
        }
        val clock: Clock = TestClock(Instant.ofEpochSecond(100))
        val logger = contextLogger()
        val bootConfig = SmartConfigFactory.create(ConfigFactory.empty())
            .create(
                ConfigFactory.parseString(
                    """
                $INSTANCE_ID = 1
                $BUS_TYPE = INMEMORY
                """
                )
            )
        const val messagingConf = """
            componentVersion="5.1"
            subscription {
                consumer {
                    close.timeout = 6000
                    poll.timeout = 6000
                    thread.stop.timeout = 6000
                    processor.retries = 3
                    subscribe.retries = 3
                    commit.retries = 3
                }
                producer {
                    close.timeout = 6000
                }
            }
        """
        const val cryptoConf = """
            dummy=1
        """
        private val membershipConfig = ConfigFactory.empty()
            .withValue(
                MAX_DURATION_BETWEEN_SYNC_REQUESTS_MINUTES,
                ConfigValueFactory.fromAnyRef(100L)
            ).withValue(
                "$TTLS.$MEMBERS_PACKAGE_UPDATE",
                ConfigValueFactory.fromAnyRef(1L)
            ).root()
            .render(ConfigRenderOptions.concise())
        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"
        const val CATEGORY = "SESSION_INIT"
        const val SCHEME = ECDSA_SECP256R1_CODE_NAME
        const val EPOCH = "5"
        const val PLATFORM_VERSION = "5000"
        val schemaVersion = ConfigurationSchemaVersion(1, 0)

        val syncId = UUID.randomUUID().toString()
        val groupId = UUID.randomUUID().toString()
        val mgmName = MemberX500Name.parse("O=MGM,C=GB,L=London").toString()
        val requesterName = MemberX500Name.parse("O=Alice,C=GB,L=London").toString()
        val participantName = MemberX500Name.parse("O=Bob,C=GB,L=London").toString()
        val mgm = HoldingIdentity(mgmName, groupId)
        val requester = HoldingIdentity(requesterName, groupId)
        val participant = HoldingIdentity(participantName, groupId)
        val members = listOf(requesterName, participantName)
        val mgmSessionKey: PublicKey by lazy {
            cryptoOpsClient.generateKeyPair(
                mgm.toCorda().shortHash.value,
                CATEGORY,
                mgm.toCorda().shortHash.value + "session",
                SCHEME
            )
        }
        val mgmInfo: MemberInfo by lazy {
            createTestMemberInfo(mgm, mgmSessionKey)
        }
        val requesterSessionKey: PublicKey by lazy {
            cryptoOpsClient.generateKeyPair(
                requester.toCorda().shortHash.value,
                CATEGORY,
                requester.toCorda().shortHash.value + "session",
                SCHEME
            )
        }
        val requesterInfo: MemberInfo by lazy {
            createTestMemberInfo(requester, requesterSessionKey)
        }
        val participantSessionKey: PublicKey by lazy {
            cryptoOpsClient.generateKeyPair(
                participant.toCorda().shortHash.value,
                CATEGORY,
                participant.toCorda().shortHash.value + "session",
                SCHEME
            )
        }
        val participantInfo: MemberInfo by lazy {
            createTestMemberInfo(participant, participantSessionKey)
        }

        @JvmStatic
        @BeforeAll
        fun setUp() {
            val coordinator =
                lifecycleCoordinatorFactory.createCoordinator<SynchronisationIntegrationTest> { e, c ->
                    if (e is StartEvent) {
                        logger.info("Starting test coordinator")
                        c.followStatusChangesByName(
                            setOf(
                                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                                LifecycleCoordinatorName.forComponent<SynchronisationProxy>(),
                                LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                                LifecycleCoordinatorName.forComponent<MembershipP2PReadService>(),
                                LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
                                LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
                                LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
                                LifecycleCoordinatorName.forComponent<GroupParametersWriterService>(),
                            )
                        )
                    } else if (e is RegistrationStatusChangeEvent) {
                        logger.info("Test coordinator is ${e.status}")
                        c.updateStatus(e.status)
                    }
                }.also { it.start() }

            setupConfig()
            groupPolicyProvider.start()
            synchronisationProxy.start()
            membershipGroupReaderProvider.start()
            stableKeyPairDecryptor.start()
            membershipP2PReadService.start()
            cryptoOpsClient.start()
            membershipQueryClient.start()
            membershipPersistenceClient.start()
            virtualNodeInfoReadService.start()
            groupParametersWriterService.start()
            configurationReadService.bootstrapConfig(bootConfig)

            eventually {
                logger.info("Waiting for required services to start...")
                assertThat(coordinator.status).isEqualTo(LifecycleStatus.UP)
                logger.info("Required services started.")
            }
        }

        fun setupConfig() {
            val publisher = publisherFactory.createPublisher(PublisherConfig("clientId"), bootConfig)
            publisher.publish(
                listOf(
                    Record(
                        CONFIG_TOPIC,
                        MESSAGING_CONFIG,
                        Configuration(messagingConf, messagingConf, 0, schemaVersion)
                    )
                )
            )
            publisher.publish(
                listOf(
                    Record(
                        CONFIG_TOPIC,
                        CRYPTO_CONFIG,
                        Configuration(cryptoConf, cryptoConf, 0, schemaVersion)
                    )
                )
            )
            publisher.publish(
                listOf(
                    Record(
                        CONFIG_TOPIC,
                        MEMBERSHIP_CONFIG,
                        Configuration(membershipConfig, membershipConfig, 0, schemaVersion)
                    )
                )
            )
            configurationReadService.start()
            configurationReadService.bootstrapConfig(bootConfig)
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            configurationReadService.stop()
        }

        @Suppress("SpreadOperator")
        private fun createTestMemberInfo(holdingIdentity: HoldingIdentity, sessionInitKey: PublicKey): MemberInfo = memberInfoFactory.create(
            sortedMapOf(
                MemberInfoExtension.PARTY_NAME to holdingIdentity.x500Name,
                MemberInfoExtension.PARTY_SESSION_KEY to keyEncodingService.encodeAsString(sessionInitKey),
                MemberInfoExtension.GROUP_ID to groupId,
                String.format(MemberInfoExtension.URL_KEY, 0) to "https://corda5.r3.com:10000",
                String.format(MemberInfoExtension.PROTOCOL_VERSION, 0) to "1",
                MemberInfoExtension.SOFTWARE_VERSION to "5.0.0",
                MemberInfoExtension.PLATFORM_VERSION to PLATFORM_VERSION,
                MemberInfoExtension.SERIAL to "1",
            ),
            sortedMapOf(
                MemberInfoExtension.STATUS to MEMBER_STATUS_ACTIVE,
                MemberInfoExtension.MODIFIED_TIME to clock.instant().toString(),
            )

        )
    }

    @Test
    fun `sync requests are processed by mgm`() {
        groupPolicyProvider.putGroupPolicy(mgm.toCorda(), MgmTestGroupPolicy())

        // Create sync request to be published
        membershipGroupReaderProvider.loadMembers(mgm.toCorda(), listOf(mgmInfo, requesterInfo, participantInfo))
        val requesterHash = merkleTreeGenerator.generateTree(listOf(requesterInfo)).root
        val byteBuffer = ByteBuffer.wrap("123".toByteArray())
        val secureHash = SecureHash("algorithm", byteBuffer)

        val syncRequest = MembershipSyncRequest(
            DistributionMetaData(
                syncId,
                clock.instant()
            ),
            requesterHash.toAvro(), BloomFilter(1, 1, 1, byteBuffer), secureHash, secureHash
        )
        val messageHeader = AuthenticatedMessageHeader(
            mgm,
            requester,
            clock.instant().truncatedTo(ChronoUnit.MILLIS).plusMillis(300000L),
            UUID.randomUUID().toString(),
            null,
            MEMBERSHIP_P2P_SUBSYSTEM
        )
        val payload = ByteBuffer.wrap(syncRequestSerializer.serialize(syncRequest))

        val requestSender = publisherFactory.createPublisher(
            PublisherConfig("membership_sync_request_test_sender"),
            bootConfig
        ).also { it.start() }

        requestSender.publish(
            listOf(
                Record(
                    P2P_IN_TOPIC,
                    UUID.randomUUID().toString(),
                    AppMessage(
                        AuthenticatedMessage(
                            messageHeader,
                            payload
                        )
                    )
                )
            )
        )

        // Start subscription to gather results of processing synchronisation command
        val completableResult = CompletableFuture<MembershipPackage>()
        val membershipPackage = subscriptionFactory.createPubSubSubscription(
            SubscriptionConfig("membership_sync_request_test_receiver", P2P_OUT_TOPIC),
            getTestProcessor { v ->
                val appMessage = v as? AppMessage ?: return@getTestProcessor
                val authenticatedMessage = appMessage.message as? AuthenticatedMessage ?: return@getTestProcessor
                val membershipPackage =
                    membershipPackageDeserializer.deserialize(authenticatedMessage.payload.array()) ?: return@getTestProcessor
                completableResult.complete(membershipPackage)
            },
            messagingConfig = bootConfig
        ).also { it.start() }.use {
            completableResult.getOrThrow(Duration.ofSeconds(5))
        }

        assertSoftly { it ->
            it.assertThat(membershipPackage).isNotNull
            it.assertThat(membershipPackage.distributionType).isEqualTo(DistributionType.SYNC)
            it.assertThat(membershipPackage.memberships.memberships).hasSize(2)
                .allSatisfy {
                    val member = memberInfoFactory.create(
                        keyValueDeserializer.deserialize(it.memberContext.array())!!.toSortedMap(),
                        keyValueDeserializer.deserialize(it.mgmContext.array())!!.toSortedMap()
                    )
                    assertThat(member.name.toString()).isIn(members)
                    assertThat(member.groupId).isEqualTo(groupId)
                    assertThat(member.status).isEqualTo(MEMBER_STATUS_ACTIVE)
                }
            it.assertThat(membershipPackage.groupParameters).isNotNull
        }
    }

    @Test
    fun `member updates are successfully received on sync message from MGM`() {
        groupPolicyProvider.putGroupPolicy(requester.toCorda(), MemberTestGroupPolicy())

        // Create membership package to be published
        val members: List<MemberInfo> = mutableListOf(participantInfo)
        val signedMembers = members.map {
            val memberInfo = keyValueSerializer.serialize(it.memberProvidedContext.toAvro())
            val memberSignature = cryptoOpsClient.sign(
                participant.toCorda().shortHash.value,
                participantSessionKey,
                SignatureSpec.ECDSA_SHA256,
                memberInfo!!,
                mapOf(
                    Verifier.SIGNATURE_SPEC to SignatureSpec.ECDSA_SHA256.signatureName
                )
            ).let { withKey ->
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(withKey.by)),
                    ByteBuffer.wrap(withKey.bytes),
                    withKey.context.toWire()
                )
            }
            val mgmSignature = cryptoOpsClient.sign(
                mgm.toCorda().shortHash.value,
                mgmSessionKey,
                SignatureSpec.ECDSA_SHA256,
                merkleTreeGenerator.generateTree(listOf(it))
                    .root.bytes,
                mapOf(
                    Verifier.SIGNATURE_SPEC to SignatureSpec.ECDSA_SHA256.signatureName
                )
            ).let { withKey ->
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(withKey.by)),
                    ByteBuffer.wrap(withKey.bytes),
                    withKey.context.toWire()
                )
            }
            SignedMemberInfo.newBuilder()
                .setMemberContext(ByteBuffer.wrap(memberInfo))
                .setMgmContext(ByteBuffer.wrap(keyValueSerializer.serialize(it.mgmProvidedContext.toAvro())))
                .setMemberSignature(memberSignature)
                .setMgmSignature(mgmSignature)
                .build()
        }
        val hash = merkleTreeGenerator.generateTree(members).root
        val membership = SignedMemberships.newBuilder()
            .setMemberships(signedMembers)
            .setHashCheck(hash.toAvro())
            .build()

        val groupParameters = KeyValuePairList(
            listOf(
                KeyValuePair(EPOCH_KEY, EPOCH),
                KeyValuePair(MPV_KEY, PLATFORM_VERSION),
                KeyValuePair(MODIFIED_TIME_KEY, Instant.now().toString()),
            ).sorted()
        )
        val serializedGroupParameters = keyValueSerializer.serialize(groupParameters)!!
        val mgmSignatureGroupParameters = cryptoOpsClient.sign(
            mgm.toCorda().shortHash.value,
            mgmSessionKey,
            SignatureSpec.ECDSA_SHA256,
            serializedGroupParameters,
            mapOf(
                Verifier.SIGNATURE_SPEC to SignatureSpec.ECDSA_SHA256.signatureName
            )
        ).let { withKey ->
            CryptoSignatureWithKey(
                ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(withKey.by)),
                ByteBuffer.wrap(withKey.bytes),
                withKey.context.toWire()
            )
        }
        val wireGroupParameters = WireGroupParameters(ByteBuffer.wrap(serializedGroupParameters), mgmSignatureGroupParameters)

        val membershipPackage = MembershipPackage.newBuilder()
            .setDistributionType(DistributionType.STANDARD)
            .setCurrentPage(0)
            .setPageCount(1)
            .setCpiAllowList(null)
            .setGroupParameters(wireGroupParameters)
            .setMemberships(
                membership
            )
            .setDistributionMetaData(
                DistributionMetaData(
                    "id",
                    clock.instant(),
                )
            )
            .build()

        // Start subscription to gather results of processing synchronisation command
        val completableResult = CompletableFuture<PersistentMemberInfo>()
        val persistentMemberListSubscription = subscriptionFactory.createPubSubSubscription(
            SubscriptionConfig("membership_updates_test_receiver", MEMBER_LIST_TOPIC),
            getTestProcessor { v ->
                completableResult.complete(v as PersistentMemberInfo)
            },
            messagingConfig = bootConfig
        ).also { it.start() }

        // Start subscription to check published group parameters
        val completableResult2 = CompletableFuture<GroupParameters>()
        val groupParametersSubscription = subscriptionFactory.createPubSubSubscription(
            SubscriptionConfig("group_parameters_test_receiver", GROUP_PARAMETERS_TOPIC),
            getTestProcessor { v ->
                completableResult2.complete(v as GroupParameters)
            },
            messagingConfig = bootConfig
        ).also { it.start() }

        // Publish membership package
        val updatesSender = publisherFactory.createPublisher(
            PublisherConfig("membership_updates_test_sender"),
            messagingConfig = bootConfig
        ).also { it.start() }
        val messageHeader = AuthenticatedMessageHeader(
            requester,
            mgm,
            clock.instant().truncatedTo(ChronoUnit.MILLIS).plusMillis(300000L),
            UUID.randomUUID().toString(),
            null,
            MEMBERSHIP_P2P_SUBSYSTEM
        )
        val payload = ByteBuffer.wrap(membershipPackageSerializer.serialize(membershipPackage))
        updatesSender.publish(
            listOf(
                Record(
                    P2P_IN_TOPIC,
                    UUID.randomUUID().toString(),
                    AppMessage(
                        AuthenticatedMessage(
                            messageHeader,
                            payload
                        )
                    )
                )
            )
        )

        // Receive and assert results
        val result = assertDoesNotThrow {
            completableResult.getOrThrow(Duration.ofSeconds(5))
        }
        persistentMemberListSubscription.close()

        assertSoftly {
            it.assertThat(result).isNotNull
            it.assertThat(result)
                .isNotNull
                .isInstanceOf(PersistentMemberInfo::class.java)
            with(result) {
                it.assertThat(viewOwningMember).isEqualTo(requester)
                val memberPublished = memberInfoFactory.create(
                    memberContext.toSortedMap(),
                    mgmContext.toSortedMap()
                )
                it.assertThat(memberPublished.groupId).isEqualTo(groupId)
                it.assertThat(memberPublished.name.toString()).isEqualTo(participant.x500Name)
                it.assertThat(memberPublished.ledgerKeys.size).isEqualTo(0)
                it.assertThat(memberPublished.status).isEqualTo(MEMBER_STATUS_ACTIVE)
                it.assertThat(memberPublished.modifiedTime).isEqualTo(clock.instant().toString())
            }

            it.assertThat(membershipPersistenceClient.getPersistedGroupParameters()!!.toAvro()).isEqualTo(groupParameters)
        }

        // Receive and assert on group parameters
        val result2 = assertDoesNotThrow {
            completableResult2.getOrThrow(Duration.ofSeconds(5))
        }
        groupParametersSubscription.close()

        assertSoftly {
            it.assertThat(result2).isNotNull
            it.assertThat(result2)
                .isNotNull
                .isInstanceOf(GroupParameters::class.java)
            with(result2) {
                it.assertThat(this.groupParameters).isEqualTo(groupParameters)
                it.assertThat(this.viewOwner).isEqualTo(requester)
            }
        }
    }

    private fun getTestProcessor(resultCollector: (Any) -> Unit) = object : PubSubProcessor<String, Any> {
        override fun onNext(
            event: Record<String, Any>
        ): CompletableFuture<Unit> {
            resultCollector(event.value!!)
            return CompletableFuture.completedFuture(Unit)
        }

        override val keyClass = String::class.java
        override val valueClass = Any::class.java
    }
}
