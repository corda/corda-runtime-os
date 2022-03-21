package net.corda.messaging.kafka.integration.subscription

import com.typesafe.config.ConfigValueFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
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
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.TEST_CONFIG
import net.corda.messaging.kafka.integration.TopicTemplates
import net.corda.messaging.kafka.integration.getDemoRecords
import net.corda.messaging.kafka.integration.getKafkaProperties
import net.corda.messaging.kafka.integration.processors.TestEventLogProcessor
import net.corda.schema.configuration.MessagingConfig
import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
class KafkaEventLogSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private val kafkaProperties = getKafkaProperties()

    private companion object {
        const val CLIENT_ID = "eventLogTestPublisher"

        //automatically created topics
        const val TOPIC1 = "EventLogTopic1"
        const val TOPIC2 = "EventLogTopic2"
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var topicAdmin: KafkaTopicAdmin

    @InjectService(timeout = 4000)
    lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `asynch publish records and then start durable subscription`() {
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.EVENT_LOG_TOPIC1_TEMPLATE)

        publisherConfig = PublisherConfig(CLIENT_ID + TOPIC1)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
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
            TEST_CONFIG,
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
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.EVENT_LOG_TOPIC2_TEMPLATE)

        publisherConfig = PublisherConfig(CLIENT_ID + TOPIC2, 1)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        val futures = publisher.publish(getDemoRecords(TOPIC2, 5, 2))
        Assertions.assertThat(futures.size).isEqualTo(1)
        futures[0].get()

        val latch = CountDownLatch(30)
        val eventLogSub1 = subscriptionFactory.createEventLogSubscription(
            SubscriptionConfig("$TOPIC2-group", TOPIC2, 1),
            TestEventLogProcessor(latch),
            TEST_CONFIG,
            null
        )

        val secondSubConfig = TEST_CONFIG.withValue(
            MessagingConfig.Boot.INSTANCE_ID,
            ConfigValueFactory.fromAnyRef(2)
        )
        val eventLogSub2 = subscriptionFactory.createEventLogSubscription(
            SubscriptionConfig("$TOPIC2-group", TOPIC2, 2),
            TestEventLogProcessor(latch),
            secondSubConfig,
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
