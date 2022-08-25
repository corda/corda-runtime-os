package net.corda.membership.impl.synchronisation

import com.typesafe.config.ConfigFactory
import net.corda.chunking.toAvro
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
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
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedMemberInfo
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.DistributionType
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.membership.p2p.MembershipSyncRequest
import net.corda.data.membership.p2p.SignedMemberships
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
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.synchronisation.dummy.MemberTestGroupPolicy
import net.corda.membership.impl.synchronisation.dummy.MgmTestGroupPolicy
import net.corda.membership.impl.synchronisation.dummy.TestCryptoOpsClient
import net.corda.membership.impl.synchronisation.dummy.TestGroupPolicyProvider
import net.corda.membership.impl.synchronisation.dummy.TestGroupReaderProvider
import net.corda.membership.impl.synchronisation.dummy.TestMembershipQueryClient
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.toSortedMap
import net.corda.membership.p2p.MembershipP2PReadService
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
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
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.test.util.eventually
import net.corda.test.util.time.TestClock
import net.corda.utilities.time.Clock
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.membership.MemberInfo
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
        lateinit var merkleTreeFactory: MerkleTreeFactory

        @InjectService(timeout = 5000)
        lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

        @InjectService(timeout = 5000)
        lateinit var cryptoOpsClient: TestCryptoOpsClient

        @InjectService(timeout = 5000)
        lateinit var membershipQueryClient: TestMembershipQueryClient

        @InjectService(timeout = 5000)
        lateinit var memberInfoFactory: MemberInfoFactory

        val merkleTreeGenerator: MerkleTreeGenerator by lazy {
            MerkleTreeGenerator(
                merkleTreeFactory,
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
        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"
        const val CATEGORY = "SESSION_INIT"
        const val SCHEME = "CORDA.ECDSA.SECP256R1"
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
            membershipP2PReadService.start()
            cryptoOpsClient.start()
            membershipQueryClient.start()
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
                MemberInfoExtension.PLATFORM_VERSION to "10",
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

        val requestSender =  publisherFactory.createPublisher(
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
        val completableResult = CompletableFuture<AppMessage>()
        val membershipPackageSubscription = subscriptionFactory.createPubSubSubscription(
            SubscriptionConfig("membership_sync_request_test_receiver", P2P_OUT_TOPIC),
            getTestProcessor { v ->
                completableResult.complete(v as AppMessage)
            },
            messagingConfig = bootConfig
        ).also { it.start() }

        val result = assertDoesNotThrow {
            completableResult.getOrThrow(Duration.ofSeconds(5))
        }
        membershipPackageSubscription.close()

        assertSoftly { it ->
            it.assertThat(result).isNotNull
            it.assertThat(result)
                .isNotNull
                .isInstanceOf(AppMessage::class.java)
            it.assertThat(result.message).isInstanceOf(AuthenticatedMessage::class.java)
            val authenticatedMessage = result.message as AuthenticatedMessage
            with(membershipPackageDeserializer.deserialize(authenticatedMessage.payload.array()) as MembershipPackage) {
                it.assertThat(this.distributionType).isEqualTo(DistributionType.SYNC)
                it.assertThat(this.memberships.memberships.size).isEqualTo(2)
                this.memberships.memberships.forEach {
                    val member = memberInfoFactory.create(
                        keyValueDeserializer.deserialize(it.memberContext.array())!!.toSortedMap(),
                        keyValueDeserializer.deserialize(it.mgmContext.array())!!.toSortedMap()
                    )
                    assertThat(member.name.toString()).isIn(members)
                    assertThat(member.groupId).isEqualTo(groupId)
                    assertThat(member.status).isEqualTo(MEMBER_STATUS_ACTIVE)
                }
            }
        }
    }

    @Test
    fun `member updates are successfully received on sync message from MGM`() {
        groupPolicyProvider.putGroupPolicy(requester.toCorda(), MemberTestGroupPolicy())

        // Create membership package to be published
        val members: List<MemberInfo> = mutableListOf(participantInfo)
        val dummySignature = CryptoSignatureWithKey(
            ByteBuffer.wrap(mgm.x500Name.toByteArray()),
            ByteBuffer.wrap(mgm.x500Name.toByteArray()),
            KeyValuePairList(
                listOf(
                    KeyValuePair("name", participant.x500Name)
                )
            )
        )
        val signedMembers = members.map {
            SignedMemberInfo.newBuilder()
                .setMemberContext(ByteBuffer.wrap(keyValueSerializer.serialize(it.memberProvidedContext.toAvro())))
                .setMgmContext(ByteBuffer.wrap(keyValueSerializer.serialize(it.mgmProvidedContext.toAvro())))
                .setMemberSignature(dummySignature)
                .setMgmSignature(dummySignature)
                .build()
        }
        val membership = SignedMemberships.newBuilder()
            .setMemberships(signedMembers)
            .setHashCheck(SecureHash("SHA-256", ByteBuffer.wrap("1234567890".toByteArray())))
            .build()

        val membershipPackage = MembershipPackage.newBuilder()
            .setDistributionType(DistributionType.STANDARD)
            .setCurrentPage(0)
            .setPageCount(1)
            .setCpiWhitelist(null)
            .setGroupParameters(null)
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
