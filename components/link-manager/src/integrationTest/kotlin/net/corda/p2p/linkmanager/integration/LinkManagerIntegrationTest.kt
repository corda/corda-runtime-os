package net.corda.p2p.linkmanager.integration

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.p2p.crypto.protocol.RevocationCheckMode
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.INBOUND_SESSIONS_CACHE_SIZE
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_MESSAGE_SIZE_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_REPLAYING_MESSAGES_PER_PEER
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.OUTBOUND_SESSIONS_CACHE_SIZE
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_TIMEOUT_KEY
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.GroupParametersReaderService
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.integration.stub.CpiInfoReadServiceStub
import net.corda.p2p.linkmanager.integration.stub.GroupPolicyProviderStub
import net.corda.p2p.linkmanager.integration.stub.MembershipQueryClientStub
import net.corda.p2p.linkmanager.integration.stub.StateManagerFactoryStub
import net.corda.p2p.linkmanager.integration.stub.VirtualNodeInfoReadServiceStub
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig.BOOT_MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.schema.configuration.StateManagerConfig
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.test.util.eventually
import net.corda.test.util.lifecycle.usingLifecycle
import net.corda.utilities.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import org.slf4j.LoggerFactory

@ExtendWith(ServiceExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LinkManagerIntegrationTest {

    companion object {
        private const val messagingConf = """
            componentVersion="5.1"
            maxAllowedMessageSize = 1000000
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
        private const val cryptoConf = """
        dummy=1
    """
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        @InjectService(timeout = 4000)
        lateinit var publisherFactory: PublisherFactory

        @InjectService(timeout = 4000)
        lateinit var subscriptionFactory: SubscriptionFactory

        @InjectService(timeout = 4000)
        lateinit var configReadService: ConfigurationReadService

        @InjectService(timeout = 4000)
        lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

        @InjectService(timeout = 4000)
        lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

        @InjectService(timeout = 4000)
        lateinit var cryptoOpsClient: CryptoOpsClient

        @InjectService(timeout = 4000)
        lateinit var membershipGroupReaderProvider: MembershipGroupReaderProvider

        @InjectService(timeout = 4000)
        lateinit var  groupParametersReaderService: GroupParametersReaderService

        @InjectService(timeout = 4000)
        lateinit var sessionEncryptionOpsClient: SessionEncryptionOpsClient

        @InjectService(timeout = 4000)
        lateinit var schemaRegistry: AvroSchemaRegistry
    }

    private val replayPeriod = 2000
    private fun createLinkManagerConfiguration(replayPeriod: Int): Config {
        val innerConfig = ConfigFactory.empty()
            .withValue(LinkManagerConfiguration.MESSAGE_REPLAY_PERIOD_KEY, ConfigValueFactory.fromAnyRef(replayPeriod))
        return ConfigFactory.empty()
            .withValue(MAX_MESSAGE_SIZE_KEY, ConfigValueFactory.fromAnyRef(1000000))
            .withValue(INBOUND_SESSIONS_CACHE_SIZE, ConfigValueFactory.fromAnyRef(100))
            .withValue(OUTBOUND_SESSIONS_CACHE_SIZE, ConfigValueFactory.fromAnyRef(100))
            .withValue(MAX_REPLAYING_MESSAGES_PER_PEER, ConfigValueFactory.fromAnyRef(100))
            .withValue(SESSION_TIMEOUT_KEY, ConfigValueFactory.fromAnyRef(10000))
            .withValue(
                LinkManagerConfiguration.REPLAY_ALGORITHM_KEY,
                ConfigFactory.empty().withValue(
                    LinkManagerConfiguration.ReplayAlgorithm.Constant.configKeyName(),
                    innerConfig.root()
                ).root()
            )
            .withValue(LinkManagerConfiguration.REVOCATION_CHECK_KEY, ConfigValueFactory.fromAnyRef(RevocationCheckMode.OFF.toString()))
    }

    private val bootstrapConfig = SmartConfigFactory.createWithoutSecurityServices()
        .create(
            ConfigFactory.empty()
                .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
                .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("INMEMORY"))
                .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
                .withValue(BOOT_MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(10000000))
        )

    private fun Publisher.publishLinkManagerConfig(config: Config) {
        val configSource = config.root().render(ConfigRenderOptions.concise())
        this.publish(
            listOf(
                Record(
                    Schemas.Config.CONFIG_TOPIC,
                    ConfigKeys.P2P_LINK_MANAGER_CONFIG,
                    Configuration(configSource, configSource, 0, ConfigurationSchemaVersion(1, 0))
                )
            )
        ).forEach { it.get() }
    }

    @BeforeEach
    fun setup() {
        configReadService.start()
        configReadService.bootstrapConfig(bootstrapConfig)
    }

    @AfterEach
    fun tearDown() {
        configReadService.stop()
    }

    @Test
    fun `Link Manager can recover from bad configuration`() {
        val groupPolicyProviderName = LifecycleCoordinatorName.forComponent<GroupPolicyProvider>()
        val groupPolicyProviderCoordinator = lifecycleCoordinatorFactory.createCoordinator(groupPolicyProviderName) { _, coordinator ->
            coordinator.updateStatus(LifecycleStatus.UP)
        }
        groupPolicyProviderCoordinator.start()

        val stateManagerName = LifecycleCoordinatorName.forComponent<StateManager>()
        val stateManagerCoordinator = lifecycleCoordinatorFactory.createCoordinator(stateManagerName) { _, coordinator ->
            coordinator.updateStatus(LifecycleStatus.UP)
        }
        stateManagerCoordinator.start()

        eventually {
            assertThat(configReadService.isRunning).isTrue
        }

        val configPublisher = publisherFactory.createPublisher(PublisherConfig("config-writer", false), bootstrapConfig)
        configPublisher.publish(
            listOf(
                Record(
                    Schemas.Config.CONFIG_TOPIC,
                    ConfigKeys.MESSAGING_CONFIG,
                    Configuration(messagingConf, messagingConf, 0,
                        ConfigurationSchemaVersion(1, 0)
                    )
                )
            )
        )
        configPublisher.publish(
            listOf(
                Record(
                    Schemas.Config.CONFIG_TOPIC,
                    ConfigKeys.CRYPTO_CONFIG,
                    Configuration(cryptoConf, cryptoConf, 0,
                        ConfigurationSchemaVersion(1, 0)
                    )
                )
            )
        )
        val linkManager = LinkManager(
            subscriptionFactory,
            publisherFactory,
            lifecycleCoordinatorFactory,
            configReadService,
            cordaAvroSerializationFactory,
            bootstrapConfig,
            GroupPolicyProviderStub(),
            VirtualNodeInfoReadServiceStub(),
            CpiInfoReadServiceStub(),
            cryptoOpsClient,
            membershipGroupReaderProvider,
            MembershipQueryClientStub(),
            groupParametersReaderService,
            StateManagerFactoryStub().create(bootstrapConfig, StateManagerConfig.StateType.P2P_SESSION),
            sessionEncryptionOpsClient,
            schemaRegistry,
        )

        linkManager.usingLifecycle {
            linkManager.start()

            logger.info("Publishing valid configuration")
            val validConfig = createLinkManagerConfiguration(replayPeriod)
            configPublisher.publishLinkManagerConfig(validConfig)
            eventually(duration = 15.seconds) {
                assertThat(linkManager.isRunning).isTrue
            }

            logger.info("Publishing invalid configuration")
            val invalidConfig = createLinkManagerConfiguration(-1)
            configPublisher.publishLinkManagerConfig(invalidConfig)
            eventually(duration = 15.seconds) {
                assertThat(linkManager.dominoTile.status).isEqualTo(LifecycleStatus.DOWN)
            }

            logger.info("Publishing valid configuration again")
            configPublisher.publishLinkManagerConfig(validConfig)
            eventually(duration = 15.seconds) {
                assertThat(linkManager.isRunning).isTrue
            }
        }
    }
}