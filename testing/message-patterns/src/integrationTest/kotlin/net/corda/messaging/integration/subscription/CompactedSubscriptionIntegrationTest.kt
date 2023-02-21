package net.corda.messaging.integration.subscription

import net.corda.db.messagebus.testkit.DBSetup
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
import net.corda.messaging.integration.TopicTemplates.Companion.COMPACTED_TOPIC1
import net.corda.messaging.integration.TopicTemplates.Companion.COMPACTED_TOPIC1_TEMPLATE
import net.corda.messaging.integration.TopicTemplates.Companion.COMPACTED_TOPIC2
import net.corda.messaging.integration.TopicTemplates.Companion.COMPACTED_TOPIC2_TEMPLATE
import net.corda.messaging.integration.getDemoRecords
import net.corda.messaging.integration.getKafkaProperties
import net.corda.messaging.integration.getStringRecords
import net.corda.messaging.integration.getTopicConfig
import net.corda.messaging.integration.processors.TestCompactedProcessor
import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
class CompactedSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private val kafkaProperties = getKafkaProperties()

    private companion object {
        const val CLIENT_ID = "integrationTestCompactedPublisher"
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

    @AfterEach
    fun afterEach() {
        topicUtils.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `create compacted topic, publish records, start compacted sub, publish again`() {
        topicUtils.createTopics(getTopicConfig(COMPACTED_TOPIC1_TEMPLATE))

        publisherConfig = PublisherConfig(CLIENT_ID + COMPACTED_TOPIC1, false)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        publisher.publish(getDemoRecords(COMPACTED_TOPIC1, 1, 5)).forEach { it.get() }

        val coordinator =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("compactedTest"))
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

        val onNextLatch = CountDownLatch(5)
        val snapshotLatch = CountDownLatch(1)
        val compactedSub = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig("$COMPACTED_TOPIC1-group", COMPACTED_TOPIC1),
            TestCompactedProcessor(snapshotLatch, onNextLatch),
            TEST_CONFIG
        )
        coordinator.followStatusChangesByName(setOf(compactedSub.subscriptionName))
        compactedSub.start()

        eventually(duration = 5.seconds, waitBetween = 200.millis) {
            assertEquals(LifecycleStatus.UP, coordinator.status)
        }
        assertTrue(snapshotLatch.await(10, TimeUnit.SECONDS))
        assertThat(onNextLatch.count).isEqualTo(5)
        publisher.publish(getDemoRecords(COMPACTED_TOPIC1, 1, 5)).forEach { it.get() }
        publisher.close()
        assertTrue(onNextLatch.await(5, TimeUnit.SECONDS))
        assertThat(snapshotLatch.count).isEqualTo(0)

        compactedSub.close()
        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertEquals(LifecycleStatus.DOWN, coordinator.status)
        }
        coordinator.stop()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `create compacted topic, publish wrong records, start compacted sub`() {
        topicUtils.createTopics(getTopicConfig(COMPACTED_TOPIC2_TEMPLATE))

        publisherConfig = PublisherConfig(CLIENT_ID + COMPACTED_TOPIC2, false)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        publisher.publish(getStringRecords(COMPACTED_TOPIC2, 1, 5)).forEach { it.get() }

        val coordinator =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("compactedTest2"))
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

        val onNextLatch = CountDownLatch(5)
        val snapshotLatch = CountDownLatch(1)
        val compactedSub = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig("$COMPACTED_TOPIC2-group", COMPACTED_TOPIC2),
            TestCompactedProcessor(snapshotLatch, onNextLatch),
            TEST_CONFIG
        )
        coordinator.followStatusChangesByName(setOf(compactedSub.subscriptionName))
        compactedSub.start()

        eventually(duration = 10.seconds, waitBetween = 200.millis) {
            assertEquals(LifecycleStatus.UP, coordinator.status)
        }
        assertTrue(snapshotLatch.await(10, TimeUnit.SECONDS))
        assertThat(onNextLatch.count).isEqualTo(5)

        publisher.close()
        compactedSub.close()
        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertEquals(LifecycleStatus.DOWN, coordinator.status)
        }
        coordinator.stop()
    }
}
