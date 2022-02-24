package net.corda.p2p.linkmanager

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.impl.ConfigurationReadServiceImpl
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.libs.configuration.publish.impl.ConfigPublisherImpl
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.BASE_REPLAY_PERIOD_KEY_POSTFIX
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.CUTOFF_REPLAY_KEY_POSTFIX
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.HEARTBEAT_MESSAGE_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITIES_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_GROUP_ID
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_X500_NAME
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_MESSAGE_SIZE_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_REPLAYING_MESSAGES_PER_PEER_POSTFIX
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MESSAGE_REPLAY_KEY_PREFIX
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.PROTOCOL_MODE_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_TIMEOUT_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSIONS_PER_COUNTERPARTIES_KEY
import net.corda.lifecycle.domino.logic.DependenciesVerifier
import net.corda.lifecycle.domino.logic.DominoTileState
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.p2p.crypto.ProtocolMode
import net.corda.schema.Schemas
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class LinkManagerIntegrationTest {

    companion object {
        private val logger = contextLogger()
    }

    private val replayPeriod = 2000
    private val linkManagerConfigTemplate = """
                {
                    $LOCALLY_HOSTED_IDENTITIES_KEY: [
                        {
                            "${LOCALLY_HOSTED_IDENTITY_X500_NAME}": "O=Alice, L=London, C=GB",
                            "${LOCALLY_HOSTED_IDENTITY_GROUP_ID}": "group-1"
                        }
                    ],
                    $MAX_MESSAGE_SIZE_KEY: 1000000,
                    $PROTOCOL_MODE_KEY: ["${ProtocolMode.AUTHENTICATION_ONLY}", "${ProtocolMode.AUTHENTICATED_ENCRYPTION}"],
                    $MESSAGE_REPLAY_KEY_PREFIX$BASE_REPLAY_PERIOD_KEY_POSTFIX: <replay-period>,
                    $MESSAGE_REPLAY_KEY_PREFIX$CUTOFF_REPLAY_KEY_POSTFIX: 10000,
                    $MESSAGE_REPLAY_KEY_PREFIX$MAX_REPLAYING_MESSAGES_PER_PEER_POSTFIX: 100,
                    $HEARTBEAT_MESSAGE_PERIOD_KEY: 2000,
                    $SESSION_TIMEOUT_KEY: 10000
                    $SESSIONS_PER_COUNTERPARTIES_KEY: 4
                }
            """.trimIndent()
    private val bootstrapConfig = SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())

    private val topicService = TopicServiceImpl()
    private val lifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
    private val subscriptionFactory = InMemSubscriptionFactory(topicService, RPCTopicServiceImpl(), lifecycleCoordinatorFactory)
    private val publisherFactory = CordaPublisherFactory(topicService, RPCTopicServiceImpl(), lifecycleCoordinatorFactory)
    private val configReadService = ConfigurationReadServiceImpl(lifecycleCoordinatorFactory, subscriptionFactory)
    private val configPublisher = publisherFactory.createPublisher(PublisherConfig("config-writer")).let {
        ConfigPublisherImpl(Schemas.Config.CONFIG_TOPIC, it)
    }

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
            1,
            StubNetworkMap(
                lifecycleCoordinatorFactory,
                subscriptionFactory,
                1,
                bootstrapConfig
            ),
            ConfigBasedLinkManagerHostingMap(
                configReadService,
                lifecycleCoordinatorFactory
            ),
            StubCryptoService(
                lifecycleCoordinatorFactory,
                subscriptionFactory,
                1,
                bootstrapConfig
            )
        )

        linkManager.use {
            linkManager.start()

            logger.info("Publishing valid configuration")
            val validConfig = ConfigFactory.parseString(linkManagerConfigTemplate.replace("<replay-period>", replayPeriod.toString()))
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
            val invalidConfig = ConfigFactory.parseString(linkManagerConfigTemplate.replace("<replay-period>", "-1"))
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
            1,
            StubNetworkMap(
                lifecycleCoordinatorFactory,
                subscriptionFactory,
                1,
                bootstrapConfig
            ),
            ConfigBasedLinkManagerHostingMap(
                configReadService,
                lifecycleCoordinatorFactory
            ),
            StubCryptoService(
                lifecycleCoordinatorFactory,
                subscriptionFactory,
                1,
                bootstrapConfig
            )
        )

        assertDoesNotThrow {
            DependenciesVerifier.verify(linkManager.dominoTile)
        }
    }

}