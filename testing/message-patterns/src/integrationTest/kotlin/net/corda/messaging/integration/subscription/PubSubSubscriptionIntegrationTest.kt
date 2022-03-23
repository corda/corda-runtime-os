package net.corda.messaging.integration.subscription

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.messaging.topic.utils.TopicUtils
import net.corda.libs.messaging.topic.utils.factory.TopicUtilsFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.integration.IntegrationTestProperties.Companion.BOOTSTRAP_SERVERS_VALUE
import net.corda.messaging.integration.IntegrationTestProperties.Companion.KAFKA_COMMON_BOOTSTRAP_SERVER
import net.corda.messaging.integration.IntegrationTestProperties.Companion.TOPIC_PREFIX
import net.corda.messaging.integration.TopicTemplates
import net.corda.messaging.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX
import net.corda.messaging.integration.getDemoRecords
import net.corda.messaging.integration.getKafkaProperties
import net.corda.messaging.integration.isDBBundle
import net.corda.messaging.integration.processors.TestPubsubProcessor
import net.corda.messaging.integration.util.DBSetup
import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
class PubSubSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private lateinit var kafkaConfig: SmartConfig

    private companion object {
        const val CLIENT_ID = "integrationTestPubSubPublisher"
        const val PUBSUB_TOPIC1 = "PubSubTopic1"

        private var isDB = false

        fun getTopicConfig(topicTemplate: String): Config {
            val template = if (isDB) {
                topicTemplate.replace(TEST_TOPIC_PREFIX,"")
            } else {
                topicTemplate
            }
            return ConfigFactory.parseString(template)
        }

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup(
            @InjectBundleContext bundleContext: BundleContext
        ) {
            if (bundleContext.isDBBundle()) {
                DBSetup.setupEntities(CLIENT_ID)
                isDB = true
            }
        }

        @Suppress("unused")
        @AfterAll
        @JvmStatic
        fun done() {
            DBSetup.close()
        }
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

    @InjectService(timeout = 4000)
    lateinit var topicUtilFactory: TopicUtilsFactory

    private lateinit var topicUtils: TopicUtils

    @BeforeEach
    fun beforeEach() {
        topicUtils = topicUtilFactory.createTopicUtils(getKafkaProperties())
        kafkaConfig = SmartConfigImpl.empty()
            .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(BOOTSTRAP_SERVERS_VALUE))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TEST_TOPIC_PREFIX))
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `start pubsub subscription and publish records`() {
        topicUtils.createTopics(getTopicConfig(TopicTemplates.PUBSUB_TOPIC1_TEMPLATE))

        val latch = CountDownLatch(1)
        val coordinator =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("pubSubTest"))
            { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
                when (event) {
                    is RegistrationStatusChangeEvent -> {
                        if (event.status == LifecycleStatus.UP) {
                            coordinator.updateStatus(LifecycleStatus.UP)
                        } else {
                            coordinator.updateStatus(LifecycleStatus.DOWN)
                        }
                    }
                }
            }
        coordinator.start()

        val pubsubSub = subscriptionFactory.createPubSubSubscription(
            SubscriptionConfig("pubSub1", PUBSUB_TOPIC1),
            TestPubsubProcessor(latch),
            null,
            kafkaConfig
        )
        coordinator.followStatusChangesByName(setOf(pubsubSub.subscriptionName))
        pubsubSub.start()

        eventually(duration = 5.seconds, waitBetween = 200.millis) {
            assertEquals(LifecycleStatus.UP, coordinator.status)
        }

        publisherConfig = PublisherConfig(CLIENT_ID + PUBSUB_TOPIC1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)

        while (latch.count > 0) {
            publisher.publish(getDemoRecords(PUBSUB_TOPIC1, 10, 2))
        }

        publisher.close()
        pubsubSub.stop()

        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertEquals(LifecycleStatus.DOWN, coordinator.status)
        }
    }
}
