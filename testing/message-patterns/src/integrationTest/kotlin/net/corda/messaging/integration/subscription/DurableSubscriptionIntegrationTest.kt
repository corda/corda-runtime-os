package net.corda.messaging.integration.subscription

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
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
import net.corda.messaging.integration.IntegrationTestProperties.Companion.TEST_CONFIG
import net.corda.messaging.integration.KafkaOnly
import net.corda.messaging.integration.TopicTemplates.Companion.DURABLE_TOPIC1
import net.corda.messaging.integration.TopicTemplates.Companion.DURABLE_TOPIC1_TEMPLATE
import net.corda.messaging.integration.TopicTemplates.Companion.DURABLE_TOPIC2_TEMPLATE
import net.corda.messaging.integration.TopicTemplates.Companion.DURABLE_TOPIC3_DLQ
import net.corda.messaging.integration.TopicTemplates.Companion.DURABLE_TOPIC3_TEMPLATE
import net.corda.messaging.integration.getDemoRecords
import net.corda.messaging.integration.getKafkaProperties
import net.corda.messaging.integration.getStringRecords
import net.corda.messaging.integration.isDBBundle
import net.corda.messaging.integration.processors.TestDurableProcessor
import net.corda.messaging.integration.processors.TestDurableStringProcessor
import net.corda.messaging.integration.util.DBSetup
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig.Bus.KAFKA_CONSUMER_MAX_POLL_INTERVAL
import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
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
class DurableSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private val kafkaProperties = getKafkaProperties()

    private companion object {
        const val CLIENT_ID = "durableTestDurablePublisher"
        //automatically created topics
        const val DURABLE_TOPIC2 = "DurableTopic2"
        const val DURABLE_TOPIC3 = "DurableTopic3"
        const val DURABLE_TOPIC4 = "DurableTopic4"

        private var isDB = false

        fun getTopicConfig(topicTemplate: String): Config {
            return ConfigFactory.parseString(topicTemplate)
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
    }

    @Test
    @KafkaOnly
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `asynch publish records and then start 2 durable subscriptions, delay 1 sub, trigger rebalance`() {
        topicUtils.createTopics(getTopicConfig(DURABLE_TOPIC1_TEMPLATE))

        publisherConfig = PublisherConfig(CLIENT_ID + DURABLE_TOPIC1, false)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        val futures = publisher.publish(getDemoRecords(DURABLE_TOPIC1, 5, 3))
        assertThat(futures.size).isEqualTo(15)
        publisher.close()

        val latch = CountDownLatch(15)
        val durableSub1 = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC1-group", DURABLE_TOPIC1),
            TestDurableProcessor(latch),
            TEST_CONFIG,
            null
        )

        val triggerRebalanceQuicklyConfig = TEST_CONFIG
            .withValue(
                KAFKA_CONSUMER_MAX_POLL_INTERVAL,
                ConfigValueFactory.fromAnyRef(1000)
            )
            .withValue(
                INSTANCE_ID,
                ConfigValueFactory.fromAnyRef(2)
            )
        //long delay to not allow sub to to try rejoin group after rebalance
        val durableSub2 = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC1-group", DURABLE_TOPIC1),
            TestDurableProcessor(latch, "", 70000),
            triggerRebalanceQuicklyConfig,
            null
        )
        durableSub1.start()
        durableSub2.start()

        assertTrue(latch.await(60, TimeUnit.SECONDS))
        durableSub1.stop()
        durableSub2.stop()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `asynch publish records and then start durable subscription`() {
        topicUtils.createTopics(getTopicConfig(DURABLE_TOPIC2_TEMPLATE))

        publisherConfig = PublisherConfig(CLIENT_ID + DURABLE_TOPIC2, false)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        val futures = publisher.publish(getDemoRecords(DURABLE_TOPIC2, 5, 2))
        assertThat(futures.size).isEqualTo(10)
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        publisher.close()

        val coordinator =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("durableTest"))
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
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC2-group", DURABLE_TOPIC2),
            TestDurableProcessor(latch),
            TEST_CONFIG,
            null
        )
        coordinator.followStatusChangesByName(setOf(durableSub.subscriptionName))
        durableSub.start()

        eventually(duration = 5.seconds, waitBetween = 200.millis) {
            assertEquals(LifecycleStatus.UP, coordinator.status)
        }

        assertTrue(latch.await(1, TimeUnit.MINUTES))
        durableSub.stop()

        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertEquals(LifecycleStatus.DOWN, coordinator.status)
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `asynch publish the wrong records and then start durable subscription`() {
        topicUtils.createTopics(getTopicConfig(DURABLE_TOPIC3_TEMPLATE))

        publisherConfig = PublisherConfig(CLIENT_ID + DURABLE_TOPIC3, false)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        val futures = publisher.publish(getStringRecords(DURABLE_TOPIC3, 5, 2))
        assertThat(futures.size).isEqualTo(10)
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        val futures2 = publisher.publish(getDemoRecords(DURABLE_TOPIC3, 5, 2))
        assertThat(futures2.size).isEqualTo(10)
        futures2.forEach { it.get(10, TimeUnit.SECONDS) }
        publisher.close()

        val latch = CountDownLatch(10)
        val dlqLatch = CountDownLatch(10)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC3-group", DURABLE_TOPIC3),
            TestDurableProcessor(latch),
            TEST_CONFIG,
            null
        )
        val dlqDurableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC3-group-dlq", DURABLE_TOPIC3_DLQ),
            TestDurableStringProcessor(dlqLatch),
            TEST_CONFIG,
            null
        )
        durableSub.start()
        dlqDurableSub.start()

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        assertTrue(dlqLatch.await(10, TimeUnit.SECONDS))

        dlqDurableSub.stop()
        durableSub.stop()
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `transactional publish records, start two durable subscription, stop subs, publish again and start subs`() {
        publisherConfig = PublisherConfig(CLIENT_ID + DURABLE_TOPIC4)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        val futures = publisher.publish(getDemoRecords(DURABLE_TOPIC4, 5, 2))
        assertThat(futures.size).isEqualTo(1)
        futures[0].get()

        val latch = CountDownLatch(30)
        val durableSub1 = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC4-group", DURABLE_TOPIC4),
            TestDurableProcessor(latch),
            TEST_CONFIG,
            null
        )

        val secondSubConfig = TEST_CONFIG.withValue(
            INSTANCE_ID,
            ConfigValueFactory.fromAnyRef(2)
        )
        val durableSub2 = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC4-group", DURABLE_TOPIC4),
            TestDurableProcessor(latch),
            secondSubConfig,
            null
        )

        durableSub1.start()
        durableSub2.start()

        durableSub1.stop()
        durableSub2.stop()

        publisher.publish(getDemoRecords(DURABLE_TOPIC4, 10, 2)).forEach { it.get() }

        durableSub1.start()
        durableSub2.start()
        assertTrue(latch.await(60, TimeUnit.SECONDS))
        durableSub1.stop()
        durableSub2.stop()
        publisher.close()
    }
}
