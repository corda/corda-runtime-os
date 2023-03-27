package net.corda.messaging.integration.subscription

import com.typesafe.config.ConfigValueFactory
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
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC1
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC1_TEMPLATE
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC2
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC2_TEMPLATE
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC3
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC3_TEMPLATE
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC4
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC4_TEMPLATE
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC5
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC5_DLQ
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC5_TEMPLATE
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC6
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC6_TEMPLATE
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC7
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC7_DLQ
import net.corda.messaging.integration.TopicTemplates.Companion.EVENT_TOPIC7_TEMPLATE
import net.corda.messaging.integration.getDemoRecords
import net.corda.messaging.integration.getKafkaProperties
import net.corda.messaging.integration.getStringRecords
import net.corda.messaging.integration.getTopicConfig
import net.corda.messaging.integration.listener.TestStateAndEventListenerStrings
import net.corda.messaging.integration.processors.TestDurableProcessor
import net.corda.messaging.integration.processors.TestDurableProcessorStrings
import net.corda.messaging.integration.processors.TestDurableStringProcessor
import net.corda.messaging.integration.processors.TestStateEventProcessor
import net.corda.messaging.integration.processors.TestStateEventProcessorStrings
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.test.util.eventually
import net.corda.utilities.millis
import net.corda.utilities.seconds
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
class StateAndEventSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        const val CLIENT_ID = "integrationTestEventPublisher"
        const val EVENTSTATE_OUTPUT2 = "EventStateOutputTopic2"
        const val EVENTSTATE_OUTPUT3 = "EventStateOutputTopic3"
        const val EVENTSTATE_OUTPUT4 = "EventStateOutputTopic4"
        const val EVENTSTATE_OUTPUT5 = "EventStateOutputTopic5"
        const val EVENTSTATE_OUTPUT6 = "EventStateOutputTopic6"
        const val EVENTSTATE_OUTPUT7 = "EventStateOutputTopic7"
        const val CONSUMER_PROCESSOR_TIMEOUT = "consumer.processor.timeout"
        const val KAFKA_CONSUMER_MAX_POLL_INTERVAL = "consumer.max.poll.interval.ms"
        const val TWENTY_FIVE_SECONDS = 25 * 1_000L
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
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @Disabled("Will remain flaky until CORE-8204 is fixed")
    fun `create topic with two partitions, start two statevent sub, publish records with two keys, no outputs`() {
        topicUtils.createTopics(getTopicConfig(EVENT_TOPIC1_TEMPLATE))

        val stateAndEventLatch = CountDownLatch(10)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC1-group", EVENT_TOPIC1),
            TestStateEventProcessor(stateAndEventLatch, false),
            TEST_CONFIG
        )

        val secondWorkerConfig = TEST_CONFIG.withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(2))
        val stateEventSub2 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC1-group", EVENT_TOPIC1),
            TestStateEventProcessor(stateAndEventLatch, true),
            secondWorkerConfig
        )

        val coordinator1 =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("stateAndEventTest1"))
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
        val coordinator2 =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("stateAndEventTest2"))
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
        coordinator1.start()
        coordinator2.start()

        coordinator1.followStatusChangesByName(setOf(stateEventSub1.subscriptionName))
        coordinator2.followStatusChangesByName(setOf(stateEventSub2.subscriptionName))

        log.info("Starting Subscriptions")
        stateEventSub1.start()
        stateEventSub2.start()

        eventually(duration = 10.seconds, waitBetween = 200.millis) {
            Assertions.assertEquals(LifecycleStatus.UP, coordinator1.status)
            Assertions.assertEquals(LifecycleStatus.UP, coordinator2.status)
        }
        log.info("Subscriptions UP")

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_TOPIC1)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        log.info("Publishing Records")
        publisher.publish(getDemoRecords(EVENT_TOPIC1, 5, 2)).forEach { it.get() }

        log.info("Waiting for subscriptions to receive")
        assertTrue(stateAndEventLatch.await(60, TimeUnit.SECONDS))

        stateEventSub1.close()
        stateEventSub2.close()

        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            Assertions.assertEquals(LifecycleStatus.DOWN, coordinator1.status)
            Assertions.assertEquals(LifecycleStatus.DOWN, coordinator2.status)
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `create topics, start one statevent sub, publish records with two keys, update state and output records and verify`() {
        topicUtils.createTopics(getTopicConfig(EVENT_TOPIC2_TEMPLATE))

        val onNextLatch1 = CountDownLatch(10)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC2-group", EVENT_TOPIC2),
            TestStateEventProcessor(onNextLatch1, true, false, EVENTSTATE_OUTPUT2),
            TEST_CONFIG
        )

        stateEventSub1.start()

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_TOPIC2)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        publisher.publish(getDemoRecords(EVENT_TOPIC2, 5, 2)).forEach { it.get() }

        assertTrue(onNextLatch1.await(60, TimeUnit.SECONDS))
        stateEventSub1.close()

        val durableLatch = CountDownLatch(10)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENTSTATE_OUTPUT2-group",  EVENTSTATE_OUTPUT2),
            TestDurableProcessor(durableLatch),
            TEST_CONFIG,
            null
        )
        durableSub.start()
        assertTrue(durableLatch.await(30, TimeUnit.SECONDS))
        durableSub.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `create topics, start statevent sub, fail processor on first attempt, publish 2 records, verify listener and outputs`() {
        topicUtils.createTopics(getTopicConfig(EVENT_TOPIC3_TEMPLATE))

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_TOPIC3)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        publisher.publish(getStringRecords(EVENT_TOPIC3, 2, 1)).forEach { it.get() }

        val onNextLatch1 = CountDownLatch(3)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC3-group", EVENT_TOPIC3),
            TestStateEventProcessorStrings(onNextLatch1, true, true, EVENTSTATE_OUTPUT3),
            TEST_CONFIG,
            TestStateAndEventListenerStrings()
        )

        stateEventSub1.start()

        assertTrue(onNextLatch1.await(60, TimeUnit.SECONDS))
        stateEventSub1.close()

        val durableLatch = CountDownLatch(2)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENTSTATE_OUTPUT3-group",  EVENTSTATE_OUTPUT3),
            TestDurableProcessorStrings(durableLatch),
            TEST_CONFIG,
            null
        )
        durableSub.start()
        assertTrue(durableLatch.await(30, TimeUnit.SECONDS))
        durableSub.close()

        val expectedSyncState = mapOf("key1" to "2")
        val expectedCommitStates = listOf(mapOf("key1" to "1"), mapOf("key1" to "2"))
        val syncPartitionLatch = CountDownLatch(1)
        val losePartitionLatch = CountDownLatch(1)
        val commitStatesLatch = CountDownLatch(2)
        val onNextLatch2 = CountDownLatch(2)
        val stateEventSub2 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC3-group-2", EVENT_TOPIC3),
            TestStateEventProcessorStrings(onNextLatch2, true, false, EVENTSTATE_OUTPUT3),
            TEST_CONFIG,
            TestStateAndEventListenerStrings(expectedCommitStates, commitStatesLatch, null,
                expectedSyncState, syncPartitionLatch, expectedSyncState, losePartitionLatch)
        )

        stateEventSub2.start()
        assertTrue(onNextLatch2.await(30, TimeUnit.SECONDS))
        assertTrue(syncPartitionLatch.await(30, TimeUnit.SECONDS))
        assertTrue(commitStatesLatch.await(30, TimeUnit.SECONDS))
        stateEventSub2.close()
        assertTrue(losePartitionLatch.await(30, TimeUnit.SECONDS))
    }

    @Test
    @Timeout(180)
    @Disabled("Will remain flaky until CORE-8204 is fixed")
    fun `create topics, start 2 statevent sub, trigger rebalance and verify completion of all records`() {
        topicUtils.createTopics(getTopicConfig(EVENT_TOPIC4_TEMPLATE))

        val onNextLatch1 = CountDownLatch(30)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC4-group", EVENT_TOPIC4),
            TestStateEventProcessor(onNextLatch1, true, false, EVENTSTATE_OUTPUT4),
            TEST_CONFIG
        )

        val longWaitProcessorConfig = TEST_CONFIG
            .withValue(CONSUMER_PROCESSOR_TIMEOUT, ConfigValueFactory.fromAnyRef(30000))
            .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(2))
        val onNextLatch2 = CountDownLatch(1)

        //fail slowly on first record. allow time for subscription to be stopped to force rebalance
        val stateEventSub2 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC4-group", EVENT_TOPIC4),
            TestStateEventProcessor(onNextLatch2, true, true, EVENTSTATE_OUTPUT4, TWENTY_FIVE_SECONDS),
            longWaitProcessorConfig
        )

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_TOPIC4)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        publisher.publish(getDemoRecords(EVENT_TOPIC4, 5, 6)).forEach { it.get() }

        stateEventSub2.start()
        assertTrue(onNextLatch2.await(50, TimeUnit.SECONDS))

        stateEventSub1.start()

        //wait until start processing
        while (onNextLatch1.count == 30L) {
            Thread.sleep(100)
        }

        //trigger rebalance
        stateEventSub2.close()

        //assert first sub picks up all the work
        assertTrue(onNextLatch1.await(180, TimeUnit.SECONDS))

        stateEventSub1.close()

        val durableLatch = CountDownLatch(10)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENTSTATE_OUTPUT4-group",  EVENTSTATE_OUTPUT4),
            TestDurableProcessor(durableLatch),
            TEST_CONFIG,
            null
        )
        durableSub.start()
        assertTrue(durableLatch.await(30, TimeUnit.SECONDS))
        durableSub.close()
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    fun `create topics, start one statevent sub, publish records, slow processor for first record, 1 record sent DLQ and verify`() {
        topicUtils.createTopics(getTopicConfig(EVENT_TOPIC5_TEMPLATE))

        val shortIntervalTimeoutConfig = TEST_CONFIG
            .withValue(KAFKA_CONSUMER_MAX_POLL_INTERVAL, ConfigValueFactory.fromAnyRef(15000))

        val stateAndEventLatch = CountDownLatch(10)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC5-group", EVENT_TOPIC5),
            TestStateEventProcessorStrings(stateAndEventLatch, true, false, EVENTSTATE_OUTPUT5, 20000),
            shortIntervalTimeoutConfig,
            TestStateAndEventListenerStrings()
        )
        stateEventSub1.start()

        //verify output records from state and event
        val durableLatch = CountDownLatch(9)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENTSTATE_OUTPUT5-group",  EVENTSTATE_OUTPUT5),
            TestDurableProcessorStrings(durableLatch),
            TEST_CONFIG,
            null
        )
        durableSub.start()

        //verify dead letter populated
        val deadLetterLatch = CountDownLatch(1)
        val deadLetterSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENTSTATE_OUTPUT5-group-DLQ",  EVENT_TOPIC5_DLQ),
            TestDurableProcessorStrings(deadLetterLatch),
            TEST_CONFIG,
            null
        )
        deadLetterSub.start()

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_TOPIC5)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        publisher.publish(getStringRecords(EVENT_TOPIC5, 5, 2)).forEach { it.get() }

        assertTrue(stateAndEventLatch.await(5, TimeUnit.MINUTES))
        assertTrue(durableLatch.await(30, TimeUnit.SECONDS))
        assertTrue(deadLetterLatch.await(30, TimeUnit.SECONDS))

        durableSub.close()
        deadLetterSub.close()
        stateEventSub1.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `create topics, start one statevent sub, publish records, slow processor and listener, all records successful`() {
        topicUtils.createTopics(getTopicConfig(EVENT_TOPIC6_TEMPLATE))

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_TOPIC6)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        publisher.publish(getStringRecords(EVENT_TOPIC6, 1, 3)).forEach { it.get() }

        val shortIntervalTimeoutConfig = TEST_CONFIG
            .withValue(KAFKA_CONSUMER_MAX_POLL_INTERVAL, ConfigValueFactory.fromAnyRef(11000))

        val stateAndEventLatch = CountDownLatch(3)
        val onCommitLatch = CountDownLatch(3)
        val expectedCommitStates = listOf(mapOf("key1" to "1"), mapOf("key2" to "2"), mapOf("key3" to "3"))

        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC6-group", EVENT_TOPIC6),
            TestStateEventProcessorStrings(stateAndEventLatch, true, false, EVENTSTATE_OUTPUT6, 5000),
            shortIntervalTimeoutConfig, TestStateAndEventListenerStrings(expectedCommitStates, onCommitLatch, 5000)
        )
        stateEventSub1.start()

        //verify output records from state and event
        val durableLatch = CountDownLatch(3)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENTSTATE_OUTPUT6-group",  EVENTSTATE_OUTPUT6),
            TestDurableProcessorStrings(durableLatch),
            TEST_CONFIG,
            null
        )
        durableSub.start()

        assertTrue(stateAndEventLatch.await(60, TimeUnit.SECONDS))
        assertTrue(durableLatch.await(60, TimeUnit.SECONDS))

        durableSub.close()
        stateEventSub1.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `create topics, start one statevent sub, publish incorrect records with two keys, update state and output records and verify`() {
        topicUtils.createTopics(getTopicConfig(EVENT_TOPIC7_TEMPLATE))

        val onNextLatch1 = CountDownLatch(10)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC7-group", EVENT_TOPIC7),
            TestStateEventProcessor(onNextLatch1, true, false, EVENTSTATE_OUTPUT7),
            TEST_CONFIG
        )

        stateEventSub1.start()

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_TOPIC7)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        publisher.publish(getDemoRecords(EVENT_TOPIC7, 5, 2)).forEach { it.get() }
        publisher.publish(getStringRecords(EVENT_TOPIC7, 5, 2)).forEach { it.get() }

        assertTrue(onNextLatch1.await(30, TimeUnit.SECONDS))
        stateEventSub1.close()

        val durableLatch = CountDownLatch(10)
        val dlqLatch = CountDownLatch(10)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENTSTATE_OUTPUT7-group",  EVENTSTATE_OUTPUT7),
            TestDurableProcessor(durableLatch),
            TEST_CONFIG,
            null
        )
        val dlqSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENT_TOPIC7-group",  EVENT_TOPIC7_DLQ),
            TestDurableStringProcessor(dlqLatch),
            TEST_CONFIG,
            null
        )
        durableSub.start()
        dlqSub.start()
        assertTrue(durableLatch.await(30, TimeUnit.SECONDS))
        assertTrue(dlqLatch.await(30, TimeUnit.SECONDS))
        durableSub.close()
        dlqSub.close()
    }
}
