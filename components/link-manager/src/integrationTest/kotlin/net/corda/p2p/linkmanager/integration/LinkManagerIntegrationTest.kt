package net.corda.p2p.linkmanager.integration

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.libs.configuration.publish.factory.ConfigPublisherFactory
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.HEARTBEAT_MESSAGE_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_MESSAGE_SIZE_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_REPLAYING_MESSAGES_PER_PEER
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSIONS_PER_PEER_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_TIMEOUT_KEY
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DependenciesVerifier
import net.corda.lifecycle.domino.logic.DominoTileState
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.LinkManager
import net.corda.schema.Schemas
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LinkManagerIntegrationTest {

    companion object {
        private val logger = contextLogger()

        @InjectService(timeout = 4000)
        lateinit var publisherFactory: PublisherFactory

        @InjectService(timeout = 4000)
        lateinit var subscriptionFactory: SubscriptionFactory

        @InjectService(timeout = 4000)
        lateinit var configReadService: ConfigurationReadService

        @InjectService(timeout = 4000)
        lateinit var lifecycleCoordinatorFactory : LifecycleCoordinatorFactory

        @InjectService(timeout = 4000)
        lateinit var configPublisherFactory: ConfigPublisherFactory
    }

    private val replayPeriod = 2000
    private fun createLinkManagerConfiguration(replayPeriod: Int): Config {
        val innerConfig = ConfigFactory.empty()
            .withValue(LinkManagerConfiguration.REPLAY_PERIOD_KEY, ConfigValueFactory.fromAnyRef(replayPeriod))
        return ConfigFactory.empty()
            .withValue(MAX_MESSAGE_SIZE_KEY, ConfigValueFactory.fromAnyRef(1000000))
            .withValue(MAX_REPLAYING_MESSAGES_PER_PEER, ConfigValueFactory.fromAnyRef(100))
            .withValue(HEARTBEAT_MESSAGE_PERIOD_KEY, ConfigValueFactory.fromAnyRef(2000))
            .withValue(SESSION_TIMEOUT_KEY, ConfigValueFactory.fromAnyRef(10000))
            .withValue(SESSIONS_PER_PEER_KEY, ConfigValueFactory.fromAnyRef(4))
            .withValue(LinkManagerConfiguration.ReplayAlgorithm.Constant.configKeyName(), innerConfig.root())
    }

    private val bootstrapConfig = SmartConfigFactory.create(ConfigFactory.empty())
        .create(ConfigFactory.empty().withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1)))

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
        eventually {
            assertThat(configReadService.isRunning).isTrue
        }

        val configPublisher = configPublisherFactory.createPublisher(
            Schemas.Config.CONFIG_TOPIC,
            SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())
        )

        val linkManager = LinkManager(
            subscriptionFactory,
            publisherFactory,
            lifecycleCoordinatorFactory,
            configReadService,
            bootstrapConfig,
        )

        linkManager.use {
            linkManager.start()

            logger.info("Publishing valid configuration")
            val validConfig = createLinkManagerConfiguration(replayPeriod)
            configPublisher.updateConfiguration(
                CordaConfigurationKey(
                    "p2p-e2e-test-runner",
                    CordaConfigurationVersion(LinkManagerConfiguration.PACKAGE_NAME, 0, 1),
                    CordaConfigurationVersion(LinkManagerConfiguration.COMPONENT_NAME, 0, 1)
                ),
                validConfig
            )
            eventually {
                assertThat(linkManager.isRunning).isTrue
            }

            logger.info("Publishing invalid configuration")
            val invalidConfig = createLinkManagerConfiguration(-1)
            configPublisher.updateConfiguration(
                CordaConfigurationKey(
                    "p2p-e2e-test-runner",
                    CordaConfigurationVersion(LinkManagerConfiguration.PACKAGE_NAME, 0, 1),
                    CordaConfigurationVersion(LinkManagerConfiguration.COMPONENT_NAME, 0, 1)
                ),
                invalidConfig
            )
            eventually {
                assertThat(linkManager.dominoTile.state).isEqualTo(DominoTileState.StoppedDueToChildStopped)
            }

            logger.info("Publishing valid configuration again")
            configPublisher.updateConfiguration(
                CordaConfigurationKey(
                    "p2p-e2e-test-runner",
                    CordaConfigurationVersion(LinkManagerConfiguration.PACKAGE_NAME, 0, 1),
                    CordaConfigurationVersion(LinkManagerConfiguration.COMPONENT_NAME, 0, 1)
                ),
                validConfig
            )
            eventually {
                assertThat(linkManager.isRunning).isTrue
            }
        }
    }

    @Test
    fun `domino logic dependencies are setup successfully for link manager`() {
        val linkManager = LinkManager(
            subscriptionFactory,
            publisherFactory,
            lifecycleCoordinatorFactory,
            configReadService,
            bootstrapConfig,
            2,
        )

        assertDoesNotThrow {
            DependenciesVerifier.verify(linkManager.dominoTile)
        }
    }
}
