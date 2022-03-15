package net.corda.messaging.integration.subscription

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
import net.corda.messaging.integration.TopicTemplates
import net.corda.messaging.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX
import net.corda.messaging.integration.getDemoRecords
import net.corda.messaging.integration.getKafkaProperties
import net.corda.messaging.integration.isDBBundle
import net.corda.messaging.integration.processors.TestEventLogProcessor
import net.corda.messaging.integration.util.DBSetup
import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
class KafkaEventLogSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private lateinit var kafkaConfig: SmartConfig
    private val kafkaProperties = getKafkaProperties()

    private companion object {
        const val CLIENT_ID = "eventLogTestPublisher"

        //automatically created topics
        const val TOPIC1 = "EventLogTopic1"
        const val TOPIC2 = "EventLogTopic2"

        private var eventLogTopic1Config = ConfigFactory.parseString(TopicTemplates.EVENT_TOPIC1_TEMPLATE)
        private var eventLogTopic2Config = ConfigFactory.parseString(TopicTemplates.EVENT_TOPIC2_TEMPLATE)

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup(
            @InjectBundleContext bundleContext: BundleContext
        ) {
            if (bundleContext.isDBBundle()) {
                DBSetup.setupEntities(CLIENT_ID)
                // Dodgy remove prefix for DB code
                eventLogTopic1Config = ConfigFactory.parseString(
                    TopicTemplates.EVENT_TOPIC1_TEMPLATE.replace(TEST_TOPIC_PREFIX,"")
                )
                eventLogTopic2Config = ConfigFactory.parseString(
                    TopicTemplates.EVENT_TOPIC2_TEMPLATE.replace(TEST_TOPIC_PREFIX,"")
                )
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
            .withValue(
                net.corda.messaging.integration.IntegrationTestProperties.KAFKA_COMMON_BOOTSTRAP_SERVER,
                ConfigValueFactory.fromAnyRef(net.corda.messaging.integration.IntegrationTestProperties.BOOTSTRAP_SERVERS_VALUE)
            )
            .withValue(net.corda.messaging.integration.IntegrationTestProperties.TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TEST_TOPIC_PREFIX))
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `asynch publish records and then start durable subscription`() {
        topicUtils.createTopics(eventLogTopic1Config)

        publisherConfig = PublisherConfig(CLIENT_ID + TOPIC1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        val futures = publisher.publish(getDemoRecords(TOPIC1, 5, 2))
        Assertions.assertThat(futures.size).isEqualTo(10)
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        publisher.close()

        val coordinator =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("eventLogTest"))
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

        val latch = CountDownLatch(10)
        val eventLogSub = subscriptionFactory.createEventLogSubscription(
            SubscriptionConfig("$TOPIC1-group", TOPIC1, 1),
            TestEventLogProcessor(latch),
            kafkaConfig,
            null
        )

        coordinator.followStatusChangesByName(setOf(eventLogSub.subscriptionName))
        eventLogSub.start()

        eventually(duration = 5.seconds, waitBetween = 200.millis) {
            assertEquals(LifecycleStatus.UP, coordinator.status)
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        eventLogSub.stop()

        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertEquals(LifecycleStatus.DOWN, coordinator.status)
        }
    }


    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `transactional publish records, start two durable subscription, stop subs, publish again and start subs`() {
        topicUtils.createTopics(eventLogTopic2Config)

        publisherConfig = PublisherConfig(CLIENT_ID + TOPIC2, 1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        val futures = publisher.publish(getDemoRecords(TOPIC2, 5, 2))
        Assertions.assertThat(futures.size).isEqualTo(1)
        futures[0].get()

        val latch = CountDownLatch(30)
        val eventLogSub1 = subscriptionFactory.createEventLogSubscription(
            SubscriptionConfig("$TOPIC2-group", TOPIC2, 1),
            TestEventLogProcessor(latch),
            kafkaConfig,
            null
        )
        val eventLogSub2 = subscriptionFactory.createEventLogSubscription(
            SubscriptionConfig("$TOPIC2-group", TOPIC2, 2),
            TestEventLogProcessor(latch),
            kafkaConfig,
            null
        )

        eventLogSub1.start()
        eventLogSub2.start()

        eventLogSub1.stop()
        eventLogSub2.stop()

        publisher.publish(getDemoRecords(TOPIC2, 10, 2)).forEach { it.get() }

        eventLogSub1.start()
        eventLogSub2.start()
        assertTrue(latch.await(40, TimeUnit.SECONDS))
        eventLogSub1.stop()
        eventLogSub2.stop()
        publisher.close()
    }

}
