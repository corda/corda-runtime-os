package net.corda.membership.impl.synchronisation
/*
import com.typesafe.config.ConfigFactory
import net.corda.chunking.toAvro
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.crypto.SecureHash
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.p2p.DistributionMetaData
import net.corda.data.membership.p2p.MembershipSyncRequest
import net.corda.data.sync.BloomFilter
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.synchronisation.dummy.MgmTestGroupPolicy
import net.corda.membership.impl.synchronisation.dummy.TestGroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.p2p.helpers.MerkleTreeGenerator
import net.corda.membership.p2p.helpers.P2pRecordsFactory.Companion.MEMBERSHIP_P2P_SUBSYSTEM
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.Schemas
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig
import net.corda.test.util.eventually
import net.corda.test.util.time.TestClock
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@ExtendWith(ServiceExtension::class, DBSetup::class)
class MgmSynchronisationIntegrationTest {
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
        lateinit var groupReaderProvider: MembershipGroupReaderProvider

        @InjectService(timeout = 5000)
        lateinit var serializationFactory: CordaAvroSerializationFactory

        @InjectService(timeout = 5000)
        lateinit var cipherSchemeMetadata: CipherSchemeMetadata

        @InjectService(timeout = 5000)
        lateinit var cryptoOpsClient: CryptoOpsClient

        @InjectService(timeout = 5000)
        lateinit var membershipQueryClient: MembershipQueryClient

        @InjectService(timeout = 5000)
        lateinit var merkleTreeFactory: MerkleTreeFactory

        @InjectService(timeout = 5000)
        lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

        @InjectService(timeout = 5000)
        lateinit var groupPolicyProvider: TestGroupPolicyProvider

        @InjectService(timeout = 5000)
        lateinit var memberInfoFactory: MemberInfoFactory

        lateinit var merkleTreeGenerator: MerkleTreeGenerator
        lateinit var syncRequestSerializer: CordaAvroSerializer<MembershipSyncRequest>

        val logger = contextLogger()
        val bootConfig = SmartConfigFactory.create(ConfigFactory.empty())
            .create(
                ConfigFactory.parseString(
                    """
                ${BootConfig.INSTANCE_ID} = 1
                ${MessagingConfig.Bus.BUS_TYPE} = INMEMORY
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
        val schemaVersion = ConfigurationSchemaVersion(1, 0)

        val SYNC_ID = UUID.randomUUID().toString()
        val clock: Clock = TestClock(Instant.ofEpochSecond(100))

        const val GROUP_ID = "dummy_group"
        val mgm = HoldingIdentity(MemberX500Name.parse("O=MGM,C=GB,L=London").toString(), GROUP_ID)
        val alice = HoldingIdentity(MemberX500Name.parse("O=Alice,C=GB,L=London").toString(), GROUP_ID)

        @JvmStatic
        @BeforeAll
        fun setUp() {
            val coordinator = lifecycleCoordinatorFactory.createCoordinator<MgmSynchronisationIntegrationTest> { e, c ->
                if (e is StartEvent) {
                    logger.info("Starting test coordinator")
                    c.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                            LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
                            LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
                            LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                        )
                    )
                } else if (e is RegistrationStatusChangeEvent) {
                    logger.info("Test coordinator is ${e.status}")
                    c.updateStatus(e.status)
                }
            }.also { it.start() }

            syncRequestSerializer = cordaAvroSerializationFactory.createAvroSerializer { }

            setupConfig()
            groupPolicyProvider.start()
            cryptoOpsClient.start()
            membershipQueryClient.start()
            configurationReadService.bootstrapConfig(bootConfig)
            groupReaderProvider.start()

            merkleTreeGenerator = MerkleTreeGenerator(
                merkleTreeFactory,
                cordaAvroSerializationFactory
            )

            eventually {
                logger.info("Waiting for required services to start...")
                assertThat(coordinator.status).isEqualTo(LifecycleStatus.UP)
                logger.info("Required services started.")
            }
        }

        fun setupConfig() {
            val publisher = publisherFactory.createPublisher(
                PublisherConfig("clientId"),
                bootConfig
            )
            publisher.publish(
                listOf(
                    Record(
                        Schemas.Config.CONFIG_TOPIC,
                        ConfigKeys.MESSAGING_CONFIG,
                        Configuration(messagingConf, messagingConf, 0, schemaVersion)
                    )
                )
            )
            configurationReadService.start()
            configurationReadService.bootstrapConfig(
                bootConfig
            )
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            configurationReadService.stop()
        }
    }

    @Test
    fun `test`() {
        groupPolicyProvider.putGroupPolicy(MgmTestGroupPolicy())

        // create sync request to be published
        val member = createMemberInfo(alice)
        val memberHash = merkleTreeGenerator.generateTree(listOf(member)).root
        val byteBuffer = ByteBuffer.wrap("123".toByteArray())
        val secureHash = SecureHash("algorithm", byteBuffer)

        val syncRequest = MembershipSyncRequest(
            DistributionMetaData(
                SYNC_ID,
                clock.instant()
            ),
            memberHash.toAvro(), BloomFilter(1, 1, 1, byteBuffer), secureHash, secureHash
        )
        val messageHeader = AuthenticatedMessageHeader(
            mgm,
            alice,
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





        // start subscription to gather results of the sync request processing

    }

    private fun createMemberInfo(identity: HoldingIdentity) = memberInfoFactory.create(
        sortedMapOf(
            GROUP_ID to GROUP_ID,
            PARTY_NAME to identity.x500Name,
            Pair(String.format(URL_KEY, "0"), "http://localhost:8080"),
            Pair(String.format(PROTOCOL_VERSION, "0"), "1"),
            PLATFORM_VERSION to "1",
            SOFTWARE_VERSION to "5.0.0"
        ),
        sortedMapOf(
            STATUS to MEMBER_STATUS_ACTIVE
        )
    )
}*/