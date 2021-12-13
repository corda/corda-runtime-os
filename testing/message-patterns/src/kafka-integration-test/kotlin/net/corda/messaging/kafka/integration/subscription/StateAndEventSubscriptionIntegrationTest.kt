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
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.BOOTSTRAP_SERVERS_VALUE
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.KAFKA_COMMON_BOOTSTRAP_SERVER
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.TOPIC_PREFIX
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.DLQ_SUFFIX
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC1
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC1_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC2
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC2_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC3
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC3_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC4
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC4_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC5
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC5_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC6
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC6_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC7
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC7_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX
import net.corda.messaging.kafka.integration.getDemoRecords
import net.corda.messaging.kafka.integration.getKafkaProperties
import net.corda.messaging.kafka.integration.getStringRecords
import net.corda.messaging.kafka.integration.listener.TestStateAndEventListenerStrings
import net.corda.messaging.kafka.integration.processors.TestDurableProcessor
import net.corda.messaging.kafka.integration.processors.TestDurableProcessorStrings
import net.corda.messaging.kafka.integration.processors.TestDurableStringProcessor
import net.corda.messaging.kafka.integration.processors.TestStateEventProcessor
import net.corda.messaging.kafka.integration.processors.TestStateEventProcessorStrings
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.CONSUMER_MAX_POLL_INTERVAL
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.CONSUMER_PROCESSOR_TIMEOUT
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.MESSAGING_KAFKA
import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.junit.jupiter.api.Assertions
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
class StateAndEventSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private lateinit var kafkaConfig: SmartConfig
    private val kafkaProperties = getKafkaProperties()

    private companion object {
        const val CLIENT_ID = "integrationTestEventPublisher"
        const val EVENTSTATE_OUTPUT2 = "EventStateOutputTopic2"
        const val EVENTSTATE_OUTPUT3 = "EventStateOutputTopic3"
        const val EVENTSTATE_OUTPUT4 = "EventStateOutputTopic4"
        const val EVENTSTATE_OUTPUT5 = "EventStateOutputTopic5"
        const val EVENTSTATE_OUTPUT6 = "EventStateOutputTopic6"
        const val EVENTSTATE_OUTPUT7 = "EventStateOutputTopic7"
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory


    @InjectService(timeout = 4000)
    lateinit var topicAdmin: KafkaTopicAdmin

    @InjectService(timeout = 4000)
    lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory


    @BeforeEach
    fun beforeEach() {
        kafkaConfig = SmartConfigImpl.empty()
            .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(BOOTSTRAP_SERVERS_VALUE))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TEST_TOPIC_PREFIX))
    }

    @Test
    fun `create topic with two partitions, start two statevent sub, publish records with two keys, no outputs`() {
        topicAdmin.createTopics(kafkaProperties, EVENT_TOPIC1_TEMPLATE)

        val stateAndEventLatch = CountDownLatch(10)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC1-group", EVENT_TOPIC1, 1),
            TestStateEventProcessor(stateAndEventLatch, false),
            kafkaConfig
        )

        val stateEventSub2 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC1-group", EVENT_TOPIC1, 2),
            TestStateEventProcessor(stateAndEventLatch, true),
            kafkaConfig
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

        stateEventSub1.start()
        stateEventSub2.start()

        eventually(duration = 10.seconds, waitBetween = 200.millis) {
            Assertions.assertEquals(LifecycleStatus.UP, coordinator1.status)
            Assertions.assertEquals(LifecycleStatus.UP, coordinator2.status)
        }

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_TOPIC1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        publisher.publish(getDemoRecords(EVENT_TOPIC1, 5, 2)).forEach { it.get() }

        assertTrue(stateAndEventLatch.await(40, TimeUnit.SECONDS))

        stateEventSub1.stop()
        stateEventSub2.stop()

        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            Assertions.assertEquals(LifecycleStatus.DOWN, coordinator1.status)
            Assertions.assertEquals(LifecycleStatus.DOWN, coordinator2.status)
        }
    }

    @Test
    fun `create topics, start one statevent sub, publish records with two keys, update state and output records and verify`() {
        topicAdmin.createTopics(kafkaProperties, EVENT_TOPIC2_TEMPLATE)

        val onNextLatch1 = CountDownLatch(10)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC2-group", EVENT_TOPIC2, 1),
            TestStateEventProcessor(onNextLatch1, true, false, EVENTSTATE_OUTPUT2),
            kafkaConfig
        )

        stateEventSub1.start()

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_TOPIC2)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        publisher.publish(getDemoRecords(EVENT_TOPIC2, 5, 2)).forEach { it.get() }

        assertTrue(onNextLatch1.await(30, TimeUnit.SECONDS))
        stateEventSub1.stop()

        val durableLatch = CountDownLatch(10)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENTSTATE_OUTPUT2-group",  EVENTSTATE_OUTPUT2, 1),
            TestDurableProcessor(durableLatch),
            kafkaConfig,
            null
        )
        durableSub.start()
        assertTrue(durableLatch.await(30, TimeUnit.SECONDS))
        durableSub.stop()
    }

    @Test
    fun `create topics, start statevent sub, fail processor on first attempt, publish 2 records, verify listener and outputs`() {
        topicAdmin.createTopics(kafkaProperties, EVENT_TOPIC3_TEMPLATE)

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_TOPIC3, 1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        publisher.publish(getStringRecords(EVENT_TOPIC3, 2, 1)).forEach { it.get() }

        val onNextLatch1 = CountDownLatch(3)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC3-group", EVENT_TOPIC3, 1),
            TestStateEventProcessorStrings(onNextLatch1, true, true, EVENTSTATE_OUTPUT3),
            kafkaConfig,
            TestStateAndEventListenerStrings()
        )

        stateEventSub1.start()

        assertTrue(onNextLatch1.await(30, TimeUnit.SECONDS))
        stateEventSub1.stop()

        val durableLatch = CountDownLatch(2)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENTSTATE_OUTPUT3-group",  EVENTSTATE_OUTPUT3, 1),
            TestDurableProcessorStrings(durableLatch),
            kafkaConfig,
            null
        )
        durableSub.start()
        assertTrue(durableLatch.await(30, TimeUnit.SECONDS))
        durableSub.stop()

        val expectedSyncState = mapOf("key1" to "2")
        val expectedCommitStates = listOf(mapOf("key1" to "1"), mapOf("key1" to "2"))
        val syncPartitionLatch = CountDownLatch(1)
        val losePartitionLatch = CountDownLatch(1)
        val commitStatesLatch = CountDownLatch(2)
        val onNextLatch2 = CountDownLatch(2)
        val stateEventSub2 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC3-group-2", EVENT_TOPIC3, 1),
            TestStateEventProcessorStrings(onNextLatch2, true, false, EVENTSTATE_OUTPUT3),
            kafkaConfig,
            TestStateAndEventListenerStrings(expectedCommitStates, commitStatesLatch, null,
                expectedSyncState, syncPartitionLatch, expectedSyncState, losePartitionLatch)
        )

        stateEventSub2.start()
        assertTrue(onNextLatch2.await(30, TimeUnit.SECONDS))
        assertTrue(syncPartitionLatch.await(30, TimeUnit.SECONDS))
        assertTrue(commitStatesLatch.await(30, TimeUnit.SECONDS))
        stateEventSub2.stop()
        assertTrue(losePartitionLatch.await(30, TimeUnit.SECONDS))
    }

    @Test
    @Timeout(180)
    fun `create topics, start 2 statevent sub, trigger rebalance and verify completion of all records`() {
        topicAdmin.createTopics(kafkaProperties, EVENT_TOPIC4_TEMPLATE)

        val onNextLatch1 = CountDownLatch(30)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC4-group", EVENT_TOPIC4, 1),
            TestStateEventProcessor(onNextLatch1, true, false, EVENTSTATE_OUTPUT4),
            kafkaConfig
        )

        val longWaitProcessorConfig = kafkaConfig
            .withValue("$MESSAGING_KAFKA.${CONSUMER_PROCESSOR_TIMEOUT}", ConfigValueFactory.fromAnyRef(30000))
        val onNextLatch2 = CountDownLatch(1)

        //fail slowly on first record. allow time for subscription to be stopped to force rebalance
        val stateEventSub2 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC4-group", EVENT_TOPIC4, 2),
            TestStateEventProcessor(onNextLatch2, true, true, EVENTSTATE_OUTPUT4, 70000),
            longWaitProcessorConfig
        )

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_TOPIC4)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        publisher.publish(getDemoRecords(EVENT_TOPIC4, 5, 6)).forEach { it.get() }

        stateEventSub2.start()
        assertTrue(onNextLatch2.await(40, TimeUnit.SECONDS))

        stateEventSub1.start()

        //wait until start processing
        while (onNextLatch1.count == 30L) {
            Thread.sleep(100)
        }

        //trigger rebalance
        stateEventSub2.stop()

        //assert first sub picks up all the work
        assertTrue(onNextLatch1.await(180, TimeUnit.SECONDS))

        stateEventSub1.stop()

        val durableLatch = CountDownLatch(10)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENTSTATE_OUTPUT4-group",  EVENTSTATE_OUTPUT4, 1),
            TestDurableProcessor(durableLatch),
            kafkaConfig,
            null
        )
        durableSub.start()
        assertTrue(durableLatch.await(30, TimeUnit.SECONDS))
        durableSub.stop()
    }

    @Test
    fun `create topics, start one statevent sub, publish records, slow processor for first record, 1 record sent DLQ and verify`() {
        topicAdmin.createTopics(kafkaProperties, EVENT_TOPIC5_TEMPLATE)

        val shortIntervalTimeoutConfig = kafkaConfig
            .withValue("$MESSAGING_KAFKA.$CONSUMER_MAX_POLL_INTERVAL", ConfigValueFactory.fromAnyRef(20000))

        val stateAndEventLatch = CountDownLatch(10)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC5-group", EVENT_TOPIC5, 1),
            TestStateEventProcessorStrings(stateAndEventLatch, true, false, EVENTSTATE_OUTPUT5, 30000),
            shortIntervalTimeoutConfig, TestStateAndEventListenerStrings()
        )
        stateEventSub1.start()

        //verify output records from state and event
        val durableLatch = CountDownLatch(9)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENTSTATE_OUTPUT5-group",  EVENTSTATE_OUTPUT5, 1),
            TestDurableProcessorStrings(durableLatch),
            kafkaConfig,
            null
        )
        durableSub.start()

        //verify dead letter populated
        val deadLetterLatch = CountDownLatch(1)
        val deadLetterSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENTSTATE_OUTPUT5-group-DLQ",  EVENT_TOPIC5 + DLQ_SUFFIX, 1),
            TestDurableProcessorStrings(deadLetterLatch),
            kafkaConfig,
            null
        )
        deadLetterSub.start()

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_TOPIC5)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        publisher.publish(getStringRecords(EVENT_TOPIC5, 5, 2)).forEach { it.get() }

        assertTrue(stateAndEventLatch.await(75, TimeUnit.SECONDS))
        assertTrue(durableLatch.await(30, TimeUnit.SECONDS))
        assertTrue(deadLetterLatch.await(30, TimeUnit.SECONDS))

        durableSub.stop()
        deadLetterSub.stop()
        stateEventSub1.stop()
    }

    @Test
    fun `create topics, start one statevent sub, publish records, slow processor and listener, all records successful`() {
        topicAdmin.createTopics(kafkaProperties, EVENT_TOPIC6_TEMPLATE)

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_TOPIC6)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        publisher.publish(getStringRecords(EVENT_TOPIC6, 1, 3)).forEach { it.get() }

        val shortIntervalTimeoutConfig = kafkaConfig
            .withValue("$MESSAGING_KAFKA.$CONSUMER_MAX_POLL_INTERVAL", ConfigValueFactory.fromAnyRef(20000))

        val stateAndEventLatch = CountDownLatch(3)
        val onCommitLatch = CountDownLatch(3)
        val expectedCommitStates = listOf(mapOf("key1" to "1"), mapOf("key2" to "2"), mapOf("key3" to "3"))

        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC6-group", EVENT_TOPIC6, 1),
            TestStateEventProcessorStrings(stateAndEventLatch, true, false, EVENTSTATE_OUTPUT6, 6000),
            shortIntervalTimeoutConfig, TestStateAndEventListenerStrings(expectedCommitStates, onCommitLatch, 5500)
        )
        stateEventSub1.start()

        //verify output records from state and event
        val durableLatch = CountDownLatch(3)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENTSTATE_OUTPUT6-group",  EVENTSTATE_OUTPUT6, 1),
            TestDurableProcessorStrings(durableLatch),
            kafkaConfig,
            null
        )
        durableSub.start()

        assertTrue(stateAndEventLatch.await(40, TimeUnit.SECONDS))
        assertTrue(durableLatch.await(30, TimeUnit.SECONDS))

        durableSub.stop()
        stateEventSub1.stop()
    }

    @Test
    fun `create topics, start one statevent sub, publish incorrect records with two keys, update state and output records and verify`() {
        topicAdmin.createTopics(kafkaProperties, EVENT_TOPIC7_TEMPLATE)

        val onNextLatch1 = CountDownLatch(10)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC7-group", EVENT_TOPIC7, 1),
            TestStateEventProcessor(onNextLatch1, true, false, EVENTSTATE_OUTPUT7),
            kafkaConfig
        )

        stateEventSub1.start()

        publisherConfig = PublisherConfig(CLIENT_ID + EVENT_TOPIC7)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        publisher.publish(getDemoRecords(EVENT_TOPIC7, 5, 2)).forEach { it.get() }
        publisher.publish(getStringRecords(EVENT_TOPIC7, 5, 2)).forEach { it.get() }

        assertTrue(onNextLatch1.await(30, TimeUnit.SECONDS))
        stateEventSub1.stop()

        val durableLatch = CountDownLatch(10)
        val dlqLatch = CountDownLatch(10)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENTSTATE_OUTPUT7-group",  EVENTSTATE_OUTPUT7, 1),
            TestDurableProcessor(durableLatch),
            kafkaConfig,
            null
        )
        val dlqSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$EVENT_TOPIC7-group",  "$EVENT_TOPIC7.DLQ", 1),
            TestDurableStringProcessor(dlqLatch),
            kafkaConfig,
            null
        )
        durableSub.start()
        dlqSub.start()
        assertTrue(durableLatch.await(30, TimeUnit.SECONDS))
        assertTrue(dlqLatch.await(30, TimeUnit.SECONDS))
        durableSub.stop()
        dlqSub.stop()
    }
}
