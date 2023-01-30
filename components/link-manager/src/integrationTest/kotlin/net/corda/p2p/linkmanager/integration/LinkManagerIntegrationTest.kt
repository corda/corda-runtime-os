package net.corda.p2p.linkmanager.integration

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.HEARTBEAT_MESSAGE_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_MESSAGE_SIZE_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_REPLAYING_MESSAGES_PER_PEER
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSIONS_PER_PEER_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_REFRESH_THRESHOLD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_TIMEOUT_KEY
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.GroupParametersReaderService
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.crypto.protocol.api.RevocationCheckMode
import net.corda.p2p.linkmanager.LinkManager
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.test.util.eventually
import net.corda.test.util.lifecycle.usingLifecycle
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import org.slf4j.LoggerFactory

@ExtendWith(ServiceExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LinkManagerIntegrationTest {

    companion object {
        private const val messagingConf = """
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
        lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

        @InjectService(timeout = 4000)
        lateinit var cryptoOpsClient: CryptoOpsClient

        @InjectService(timeout = 4000)
        lateinit var membershipGroupReaderProvider: MembershipGroupReaderProvider

        @InjectService(timeout = 4000)
        lateinit var  groupParametersReaderService: GroupParametersReaderService

    }

    private val replayPeriod = 2000
    private fun createLinkManagerConfiguration(replayPeriod: Int): Config {
        val innerConfig = ConfigFactory.empty()
            .withValue(LinkManagerConfiguration.MESSAGE_REPLAY_PERIOD_KEY, ConfigValueFactory.fromAnyRef(replayPeriod))
        return ConfigFactory.empty()
            .withValue(MAX_MESSAGE_SIZE_KEY, ConfigValueFactory.fromAnyRef(1000000))
            .withValue(MAX_REPLAYING_MESSAGES_PER_PEER, ConfigValueFactory.fromAnyRef(100))
            .withValue(HEARTBEAT_MESSAGE_PERIOD_KEY, ConfigValueFactory.fromAnyRef(2000))
            .withValue(SESSION_TIMEOUT_KEY, ConfigValueFactory.fromAnyRef(10000))
            .withValue(SESSIONS_PER_PEER_KEY, ConfigValueFactory.fromAnyRef(4))
            .withValue(SESSION_REFRESH_THRESHOLD_KEY, ConfigValueFactory.fromAnyRef(432000))
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
            bootstrapConfig,
            mock(GroupPolicyProvider::class.java),
            mock(VirtualNodeInfoReadService::class.java),
            mock(CpiInfoReadService::class.java),
            cryptoOpsClient,
            membershipGroupReaderProvider,
            mock(MembershipQueryClient::class.java),
            groupParametersReaderService,
        )

        linkManager.usingLifecycle {
            linkManager.start()

            logger.info("Publishing valid configuration")
            val validConfig = createLinkManagerConfiguration(replayPeriod)
            configPublisher.publishLinkManagerConfig(validConfig)
            eventually {
                assertThat(linkManager.isRunning).isTrue
            }

            logger.info("Publishing invalid configuration")
            val invalidConfig = createLinkManagerConfiguration(-1)
            configPublisher.publishLinkManagerConfig(invalidConfig)
            eventually {
                assertThat(linkManager.dominoTile.status).isEqualTo(LifecycleStatus.DOWN)
            }

            logger.info("Publishing valid configuration again")
            configPublisher.publishLinkManagerConfig(validConfig)
            eventually {
                assertThat(linkManager.isRunning).isTrue
            }
        }
    }
}