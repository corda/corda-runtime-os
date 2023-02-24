package net.corda.messaging.integration.subscription

import com.typesafe.config.ConfigValueFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import net.corda.data.demo.DemoRecord
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
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.integration.IntegrationTestProperties.Companion.TEST_CONFIG
import net.corda.messaging.integration.TopicTemplates
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_LOG_TOPIC1
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_LOG_TOPIC2
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_LOG_TOPIC2_OUTPUT
import net.corda.messaging.integration.getDemoRecords
import net.corda.messaging.integration.getKafkaProperties
import net.corda.messaging.integration.getTopicConfig
import net.corda.messaging.integration.processors.TestEventLogProcessor
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.test.util.eventually
import net.corda.utilities.millis
import net.corda.utilities.seconds
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

@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
class EventLogSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher

    private companion object {
        const val CLIENT_ID = "eventLogTestPublisher"
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
    fun `asynch publish records and then start durable subscription`() {
        topicUtils.createTopics(getTopicConfig(TopicTemplates.EVENT_LOG_TOPIC1_TEMPLATE))

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_LOG_TOPIC1, false)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        val futures = publisher.publish(getDemoRecords(EVENT_LOG_TOPIC1, 5, 2))
        assertThat(futures.size).isEqualTo(10)
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
            SubscriptionConfig("$EVENT_LOG_TOPIC1-group", EVENT_LOG_TOPIC1),
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
        eventLogSub.close()

        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertEquals(LifecycleStatus.DOWN, coordinator.status)
        }
    }


    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `transactional publish records, start two durable subscription, stop subs, publish again and start subs`() {
        topicUtils.createTopics(getTopicConfig(TopicTemplates.EVENT_LOG_TOPIC2_TEMPLATE))

        val coordinator =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("eventLogTest2"))
            { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
                when (event) {
                    is RegistrationStatusChangeEvent -> {
                            coordinator.updateStatus(event.status)
                    }
                }
            }
        coordinator.start()

        //publish 20 records
        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_LOG_TOPIC2)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        publisher.publish(getDemoRecords(EVENT_LOG_TOPIC2, 5, 4)).forEach { it.get() }

        //Start 2 subscriptions
        val confirmProcessorLatch = CountDownLatch(20)
        val eventLogSub1 = createSub(TestEventLogProcessor(latch = confirmProcessorLatch, outputTopic = EVENT_LOG_TOPIC2_OUTPUT, id = "1"))
        val eventLogSub2 = createSub(TestEventLogProcessor(latch = confirmProcessorLatch, outputTopic = EVENT_LOG_TOPIC2_OUTPUT, id = "2"))
        coordinator.followStatusChangesByName(setOf(eventLogSub1.subscriptionName, eventLogSub2.subscriptionName))
        eventLogSub1.start()
        eventLogSub2.start()

        //verify subscription has started and has begun processing
        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertThat(coordinator.status).isEqualTo(LifecycleStatus.UP)
            assertThat(confirmProcessorLatch.count).isEqualTo(0)
        }

        //close subscription
        eventLogSub1.close()
        eventLogSub2.close()

        //publish 20 more records and start new subscriptions
        publisher.publish(getDemoRecords(EVENT_LOG_TOPIC2, 5, 4)).forEach { it.get() }
        val eventLogSub1part2 = createSub(TestEventLogProcessor(outputTopic = EVENT_LOG_TOPIC2_OUTPUT, id = "3"))
        val eventLogSub2part2 = createSub(TestEventLogProcessor(outputTopic = EVENT_LOG_TOPIC2_OUTPUT, id = "4"))
        eventLogSub1part2.start()
        eventLogSub2part2.start()

        //verify all 40 records make it to output topic
        val verifyOutputs = CountDownLatch(40)
        val verifySub = createSub(TestEventLogProcessor(verifyOutputs, null, "1"), EVENT_LOG_TOPIC2_OUTPUT)
        verifySub.start()
        verifyOutputs.await(10, TimeUnit.SECONDS)

        //close resources
        verifySub.close()
        eventLogSub1part2.close()
        eventLogSub2part2.close()
        publisher.close()
    }

    fun createSub(processor: TestEventLogProcessor, topic: String = EVENT_LOG_TOPIC2): Subscription<String, DemoRecord> {
        val config = TEST_CONFIG.withValue(
            INSTANCE_ID,
            ConfigValueFactory.fromAnyRef(processor.id)
        )
        return subscriptionFactory.createEventLogSubscription(
            SubscriptionConfig("$topic-group", topic),
            processor,
            config,
            null
        )
    }
}
