package net.corda.membership.impl.synchronisation

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
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
import net.corda.data.membership.p2p.SignedMemberships
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
import net.corda.membership.impl.synchronisation.dummy.TestGroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.toSortedMap
import net.corda.membership.p2p.MembershipP2PReadService
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
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
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.test.util.eventually
import net.corda.test.util.time.TestClock
import net.corda.utilities.time.Clock
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CompletableFuture

@ExtendWith(ServiceExtension::class, DBSetup::class)
class MemberSynchronisationIntegrationTest {
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
        lateinit var membershipGroupReaderProvider: MembershipGroupReaderProvider

        @InjectService(timeout = 5000)
        lateinit var membershipP2PReadService: MembershipP2PReadService

        @InjectService(timeout = 5000)
        lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

        @InjectService(timeout = 5000)
        lateinit var cryptoOpsClient: CryptoOpsClient

        @InjectService(timeout = 5000)
        lateinit var membershipQueryClient: MembershipQueryClient

        @InjectService(timeout = 5000)
        lateinit var memberInfoFactory: MemberInfoFactory

        lateinit var keyValueSerializer: CordaAvroSerializer<KeyValuePairList>
        lateinit var membershipPackageSerializer: CordaAvroSerializer<MembershipPackage>
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
        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"
        val schemaVersion = ConfigurationSchemaVersion(1, 0)

        val groupId = UUID.randomUUID().toString()
        val source = HoldingIdentity(MemberX500Name.parse("O=MGM,C=GB,L=London").toString(), groupId)
        val destination = HoldingIdentity(MemberX500Name.parse("O=Alice,C=GB,L=London").toString(), groupId)
        val participant = HoldingIdentity(MemberX500Name.parse("O=Bob,C=GB,L=London").toString(), groupId)

        @JvmStatic
        @BeforeAll
        fun setUp() {
            val coordinator =
                lifecycleCoordinatorFactory.createCoordinator<MemberSynchronisationIntegrationTest> { e, c ->
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

            keyValueSerializer = cordaAvroSerializationFactory.createAvroSerializer { }
            membershipPackageSerializer = cordaAvroSerializationFactory.createAvroSerializer { }

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
                Assertions.assertThat(coordinator.status).isEqualTo(LifecycleStatus.UP)
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
            configurationReadService.start()
            configurationReadService.bootstrapConfig(bootConfig)
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            configurationReadService.stop()
        }
    }

    @Test
    fun `member updates are are successfully received on sync message from MGM`() {
        groupPolicyProvider.putGroupPolicy(MemberTestGroupPolicy())

        // Create membership package to be published
        val members: List<MemberInfo> = mutableListOf(createTestMemberInfo(participant))
        val dummySignature = CryptoSignatureWithKey(
            ByteBuffer.wrap(source.x500Name.toByteArray()),
            ByteBuffer.wrap(source.x500Name.toByteArray()),
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
        val registrationRequestSubscription = subscriptionFactory.createPubSubSubscription(
            SubscriptionConfig("membership_updates_test_receiver", MEMBER_LIST_TOPIC),
            getTestProcessor { v ->
                completableResult.complete(v)
            },
            messagingConfig = bootConfig
        ).also { it.start() }

        // Publish membership package
        val updatesSender = publisherFactory.createPublisher(
            PublisherConfig("membership_updates_test_sender"),
            messagingConfig = bootConfig
        ).also { it.start() }
        val messageHeader = AuthenticatedMessageHeader(
            destination,
            source,
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
        registrationRequestSubscription.close()

        SoftAssertions.assertSoftly {
            it.assertThat(result).isNotNull
            it.assertThat(result)
                .isNotNull
                .isInstanceOf(PersistentMemberInfo::class.java)
            with(result) {
                it.assertThat(viewOwningMember).isEqualTo(destination)
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

    @Suppress("SpreadOperator")
    private fun createTestMemberInfo(holdingIdentity: HoldingIdentity): MemberInfo = memberInfoFactory.create(
        sortedMapOf(
            MemberInfoExtension.PARTY_NAME to holdingIdentity.x500Name,
            MemberInfoExtension.PARTY_SESSION_KEY to "dummy-session-key",
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

    private fun getTestProcessor(resultCollector: (PersistentMemberInfo) -> Unit): PubSubProcessor<String, PersistentMemberInfo> {
        class TestProcessor : PubSubProcessor<String, PersistentMemberInfo> {
            override fun onNext(
                event: Record<String, PersistentMemberInfo>
            ): CompletableFuture<Unit> {
                resultCollector(event.value!!)
                return CompletableFuture.completedFuture(Unit)
            }

            override val keyClass = String::class.java
            override val valueClass = PersistentMemberInfo::class.java
        }
        return TestProcessor()
    }
}
