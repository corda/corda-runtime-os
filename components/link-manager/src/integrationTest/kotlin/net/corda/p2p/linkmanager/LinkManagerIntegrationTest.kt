package net.corda.p2p.linkmanager

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.impl.ConfigurationReadServiceImpl
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.publish.CordaConfigurationKey
import net.corda.libs.configuration.publish.CordaConfigurationVersion
import net.corda.libs.configuration.publish.impl.ConfigPublisherImpl
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.p2p.P2PLayerEndToEndTest
import net.corda.p2p.crypto.ProtocolMode
import net.corda.schema.Schemas
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class LinkManagerIntegrationTest {

    companion object {
        private val logger = contextLogger()
    }

    val replayPeriod = 2000
    private val linkManagerConfigTemplate = """
                {
                    ${LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITIES_KEY}: [
                        {
                            "${LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITY_X500_NAME}": "O=Alice, L=London, C=GB",
                            "${LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITY_GPOUP_ID}": "group-1"
                        }
                    ],
                    ${LinkManagerConfiguration.MAX_MESSAGE_SIZE_KEY}: 1000000,
                    ${LinkManagerConfiguration.PROTOCOL_MODE_KEY}: ["${ProtocolMode.AUTHENTICATION_ONLY}", "${ProtocolMode.AUTHENTICATED_ENCRYPTION}"],
                    ${LinkManagerConfiguration.MESSAGE_REPLAY_PERIOD_KEY}: <replay-period>,
                    ${LinkManagerConfiguration.HEARTBEAT_MESSAGE_PERIOD_KEY}: 2000,
                    ${LinkManagerConfiguration.SESSION_TIMEOUT_KEY}: 10000
                }
            """.trimIndent()
    private val bootstrapConfig = SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())

    val topicService = TopicServiceImpl()
    val lifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
    val subscriptionFactory = InMemSubscriptionFactory(topicService, RPCTopicServiceImpl(), lifecycleCoordinatorFactory)
    val publisherFactory = CordaPublisherFactory(topicService, RPCTopicServiceImpl(), lifecycleCoordinatorFactory)
    val configReadService = ConfigurationReadServiceImpl(lifecycleCoordinatorFactory, subscriptionFactory)
    val configPublisher = publisherFactory.createPublisher(PublisherConfig("config-writer")).let {
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
                assertThat(linkManager.dominoTile.state).isEqualTo(DominoTile.State.StoppedDueToError)
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
}