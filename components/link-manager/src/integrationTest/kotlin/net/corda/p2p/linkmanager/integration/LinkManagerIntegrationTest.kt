package net.corda.p2p.linkmanager.integration

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.config.Configuration
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.HEARTBEAT_MESSAGE_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_MESSAGE_SIZE_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_REPLAYING_MESSAGES_PER_PEER
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSIONS_PER_PEER_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_TIMEOUT_KEY
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DependenciesVerifier
import net.corda.lifecycle.domino.logic.DominoTileState
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.linkmanager.LinkManager
import net.corda.schema.Schemas
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
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
        .create(ConfigFactory.empty()
            .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
            .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("INMEMORY"))
        )

    private fun Publisher.publishLinkManagerConfig(config: Config) {
        this.publish(listOf(Record(
            Schemas.Config.CONFIG_TOPIC,
            "${LinkManagerConfiguration.PACKAGE_NAME}.${LinkManagerConfiguration.COMPONENT_NAME}",
            Configuration(config.root().render(ConfigRenderOptions.concise()), "0.1")
        ))).forEach { it.get() }
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
        eventually {
            assertThat(configReadService.isRunning).isTrue
        }

        val configPublisher = publisherFactory.createPublisher(PublisherConfig("config-writer", false), bootstrapConfig)

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
            configPublisher.publishLinkManagerConfig(validConfig)
            eventually {
                assertThat(linkManager.isRunning).isTrue
            }

            logger.info("Publishing invalid configuration")
            val invalidConfig = createLinkManagerConfiguration(-1)
            configPublisher.publishLinkManagerConfig(invalidConfig)
            eventually {
                assertThat(linkManager.dominoTile.state).isEqualTo(DominoTileState.StoppedDueToChildStopped)
            }

            logger.info("Publishing valid configuration again")
            configPublisher.publishLinkManagerConfig(validConfig)
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
        )

        assertDoesNotThrow {
            DependenciesVerifier.verify(linkManager.dominoTile)
        }
    }
}
