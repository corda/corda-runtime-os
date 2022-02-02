package net.corda.messaging.kafka.integration.subscription

import com.typesafe.config.ConfigValueFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
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
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.BOOTSTRAP_SERVERS_VALUE
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.KAFKA_COMMON_BOOTSTRAP_SERVER
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.TOPIC_PREFIX
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.COMPACTED_TOPIC1
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.COMPACTED_TOPIC1_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.COMPACTED_TOPIC2
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.COMPACTED_TOPIC2_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX
import net.corda.messaging.kafka.integration.getDemoRecords
import net.corda.messaging.kafka.integration.getKafkaProperties
import net.corda.messaging.kafka.integration.getStringRecords
import net.corda.messaging.kafka.integration.processors.TestCompactedProcessor
import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
class CompactedSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private lateinit var kafkaConfig: SmartConfig
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
    lateinit var topicAdmin: KafkaTopicAdmin

    @BeforeEach
    fun beforeEach() {
        kafkaConfig = SmartConfigImpl.empty()
            .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(BOOTSTRAP_SERVERS_VALUE))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TEST_TOPIC_PREFIX))
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `create compacted topic, publish records, start compacted sub, publish again`() {
        topicAdmin.createTopics(kafkaProperties, COMPACTED_TOPIC1_TEMPLATE)

        publisherConfig = PublisherConfig(CLIENT_ID + COMPACTED_TOPIC1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
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
            SubscriptionConfig("$COMPACTED_TOPIC1-group", COMPACTED_TOPIC1, 1),
            TestCompactedProcessor(snapshotLatch, onNextLatch),
            kafkaConfig
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

        compactedSub.stop()
        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertEquals(LifecycleStatus.DOWN, coordinator.status)
        }
        coordinator.stop()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `create compacted topic, publish wrong records, start compacted sub`() {
        topicAdmin.createTopics(kafkaProperties, COMPACTED_TOPIC2_TEMPLATE)

        publisherConfig = PublisherConfig(CLIENT_ID + COMPACTED_TOPIC2)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
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
            SubscriptionConfig("$COMPACTED_TOPIC2-group", COMPACTED_TOPIC2, 1),
            TestCompactedProcessor(snapshotLatch, onNextLatch),
            kafkaConfig
        )
        coordinator.followStatusChangesByName(setOf(compactedSub.subscriptionName))
        compactedSub.start()

        eventually(duration = 10.seconds, waitBetween = 200.millis) {
            assertEquals(LifecycleStatus.UP, coordinator.status)
        }
        assertTrue(snapshotLatch.await(10, TimeUnit.SECONDS))
        assertThat(onNextLatch.count).isEqualTo(5)

        publisher.close()
        compactedSub.stop()
        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertEquals(LifecycleStatus.DOWN, coordinator.status)
        }
        coordinator.stop()
    }
}
