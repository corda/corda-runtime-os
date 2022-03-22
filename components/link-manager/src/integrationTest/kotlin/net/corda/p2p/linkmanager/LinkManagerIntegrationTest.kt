package net.corda.p2p.linkmanager

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.impl.ConfigurationReadServiceImpl
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.libs.configuration.publish.impl.ConfigPublisherImpl
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.BASE_REPLAY_PERIOD_KEY_POSTFIX
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.CUTOFF_REPLAY_KEY_POSTFIX
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.HEARTBEAT_MESSAGE_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_MESSAGE_SIZE_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_REPLAYING_MESSAGES_PER_PEER_POSTFIX
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MESSAGE_REPLAY_KEY_PREFIX
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_TIMEOUT_KEY
import net.corda.lifecycle.domino.logic.DependenciesVerifier
import net.corda.lifecycle.domino.logic.DominoTileState
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.schema.Schemas
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class LinkManagerIntegrationTest {

    companion object {
        private val logger = contextLogger()
    }

    private val replayPeriod = 2000
    private fun createLinkManagerConfiguration(replayPeriod: Int): Config {
        return ConfigFactory.empty()
            .withValue(MAX_MESSAGE_SIZE_KEY, ConfigValueFactory.fromAnyRef(1000000))
            .withValue("$MESSAGE_REPLAY_KEY_PREFIX$BASE_REPLAY_PERIOD_KEY_POSTFIX", ConfigValueFactory.fromAnyRef(replayPeriod))
            .withValue("$MESSAGE_REPLAY_KEY_PREFIX$CUTOFF_REPLAY_KEY_POSTFIX", ConfigValueFactory.fromAnyRef(10000))
            .withValue("$MESSAGE_REPLAY_KEY_PREFIX$MAX_REPLAYING_MESSAGES_PER_PEER_POSTFIX", ConfigValueFactory.fromAnyRef(100))
            .withValue(HEARTBEAT_MESSAGE_PERIOD_KEY, ConfigValueFactory.fromAnyRef(2000))
            .withValue(SESSION_TIMEOUT_KEY, ConfigValueFactory.fromAnyRef(10000))
    }

    private val bootstrapConfig = SmartConfigFactory.create(ConfigFactory.empty())
        .create(ConfigFactory.empty().withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1)))

    private val topicService = TopicServiceImpl()
    private val lifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
    private val subscriptionFactory = InMemSubscriptionFactory(topicService, RPCTopicServiceImpl(), lifecycleCoordinatorFactory)
    private val publisherFactory = CordaPublisherFactory(topicService, RPCTopicServiceImpl(), lifecycleCoordinatorFactory)
    private val configReadService = ConfigurationReadServiceImpl(lifecycleCoordinatorFactory, subscriptionFactory)
    private val configPublisher = ConfigPublisherImpl(
        Schemas.Config.CONFIG_TOPIC,
        publisherFactory.createPublisher(PublisherConfig("config-writer"), bootstrapConfig)
    )

    @Test
    fun `Link Manager can recover from bad configuration`() {
        configReadService.start()
        configReadService.bootstrapConfig(bootstrapConfig)
        eventually {
            assertThat(configReadService.isRunning).isTrue
        }

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
        )

        assertDoesNotThrow {
            DependenciesVerifier.verify(linkManager.dominoTile)
        }
    }
}
