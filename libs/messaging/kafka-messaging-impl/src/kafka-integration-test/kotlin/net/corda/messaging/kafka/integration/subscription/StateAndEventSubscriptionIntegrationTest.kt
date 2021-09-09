package net.corda.messaging.kafka.integration.subscription

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.BOOTSTRAP_SERVERS_VALUE
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.KAFKA_COMMON_BOOTSTRAP_SERVER
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.TOPIC_PREFIX
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC1
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC1_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC2
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC2_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC3
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC3_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC4
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC4_TEMPLATE
import net.corda.messaging.kafka.integration.getKafkaProperties
import net.corda.messaging.kafka.integration.getRecords
import net.corda.messaging.kafka.integration.getStringRecords
import net.corda.messaging.kafka.integration.listener.TestStateAndEventListenerStrings
import net.corda.messaging.kafka.integration.processors.TestDurableProcessor
import net.corda.messaging.kafka.integration.processors.TestDurableProcessorStrings
import net.corda.messaging.kafka.integration.processors.TestStateEventProcessor
import net.corda.messaging.kafka.integration.processors.TestStateEventProcessorStrings
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_MAX_POLL_INTERVAL
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.MESSAGING_KAFKA
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
@Disabled
class StateAndEventSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private lateinit var kafkaConfig: Config
    private val kafkaProperties = getKafkaProperties()

    private companion object {
        const val CLIENT_ID = "integrationTestEventPublisher"
        const val EVENTSTATE_OUTPUT2 = "EventStateOutputTopic2"
        const val EVENTSTATE_OUTPUT3 = "EventStateOutputTopic3"
        const val EVENTSTATE_OUTPUT4 = "EventStateOutputTopic4"
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory


    @InjectService(timeout = 4000)
    lateinit var topicAdmin: KafkaTopicAdmin

    @BeforeEach
    fun beforeEach() {
        kafkaConfig = ConfigFactory.empty()
            .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(BOOTSTRAP_SERVERS_VALUE))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
    }

    @Test
    fun `create topic with two partitions, start two statevent sub, publish records with two keys, no outputs`() {
        topicAdmin.createTopics(kafkaProperties, EVENT_TOPIC1_TEMPLATE)

        val onNextLatch1 = CountDownLatch(5)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC1-group", EVENT_TOPIC1, 1),
            TestStateEventProcessor(onNextLatch1, false),
            kafkaConfig
        )

        val onNextLatch2 = CountDownLatch(5)
        val stateEventSub2 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC1-group", EVENT_TOPIC1, 2),
            TestStateEventProcessor(onNextLatch2, true),
            kafkaConfig
        )

        stateEventSub1.start()
        stateEventSub2.start()

        publisherConfig = PublisherConfig(CLIENT_ID)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        publisher.publish(getRecords(EVENT_TOPIC1, 5, 2)).forEach { it.get() }

        assertTrue(onNextLatch1.await(30, TimeUnit.SECONDS))
        assertTrue(onNextLatch2.await(10, TimeUnit.SECONDS))

        stateEventSub1.stop()
        stateEventSub2.stop()
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

        publisherConfig = PublisherConfig(CLIENT_ID)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        publisher.publish(getRecords(EVENT_TOPIC2, 5, 2)).forEach { it.get() }

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

        val onNextLatch1 = CountDownLatch(3)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC3-group", EVENT_TOPIC3, 1),
            TestStateEventProcessorStrings(onNextLatch1, true, true, EVENTSTATE_OUTPUT3),
            kafkaConfig,
            TestStateAndEventListenerStrings()
        )

        stateEventSub1.start()

        publisherConfig = PublisherConfig(CLIENT_ID, 1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        publisher.publish(getStringRecords(EVENT_TOPIC3, 2, 1)).forEach { it.get() }

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
            TestStateAndEventListenerStrings(expectedCommitStates, commitStatesLatch, expectedSyncState, syncPartitionLatch,
                expectedSyncState,
                losePartitionLatch)
        )

        stateEventSub2.start()
        assertTrue(onNextLatch2.await(20, TimeUnit.SECONDS))
        assertTrue(syncPartitionLatch.await(20, TimeUnit.SECONDS))
        assertTrue(commitStatesLatch.await(20, TimeUnit.SECONDS))
        stateEventSub2.stop()
        assertTrue(losePartitionLatch.await(20, TimeUnit.SECONDS))
    }

    @Test
    fun `create topics, start 2 statevent sub, force 1 sub to timeout, trigger rebalance and verify completion of all records`() {
        topicAdmin.createTopics(kafkaProperties, EVENT_TOPIC4_TEMPLATE)

        val onNextLatch1 = CountDownLatch(15)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC4-group", EVENT_TOPIC4, 1),
            TestStateEventProcessor(onNextLatch1, true, false, EVENTSTATE_OUTPUT4),
            kafkaConfig
        )

        val triggerRebalanceQuicklyConfig = kafkaConfig
            .withValue("$MESSAGING_KAFKA.$CONSUMER_MAX_POLL_INTERVAL", ConfigValueFactory.fromAnyRef(1000))
        //long delay to not allow sub to to try rejoin group after rebalance
        val stateEventSub2 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC4-group", EVENT_TOPIC4, 2),
            TestStateEventProcessor(onNextLatch1, true, false, EVENTSTATE_OUTPUT4, 100000),
            triggerRebalanceQuicklyConfig
        )

        stateEventSub1.start()
        stateEventSub2.start()

        publisherConfig = PublisherConfig(CLIENT_ID)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        publisher.publish(getRecords(EVENT_TOPIC4, 5, 3)).forEach { it.get() }

        assertTrue(onNextLatch1.await(90, TimeUnit.SECONDS))
        stateEventSub1.stop()
        stateEventSub2.stop()

        val durableLatch = CountDownLatch(15)
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
}
