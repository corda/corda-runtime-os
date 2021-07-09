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
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENTSTATE_OUTPUT2
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENTSTATE_TOPIC1_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENTSTATE_TOPIC2_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC1
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC1_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC2
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.EVENT_TOPIC2_TEMPLATE
import net.corda.messaging.kafka.integration.getKafkaProperties
import net.corda.messaging.kafka.integration.getRecords
import net.corda.messaging.kafka.integration.processors.TestDurableProcessor
import net.corda.messaging.kafka.integration.processors.TestStateEventProcessor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
class StateAndEventSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private lateinit var kafkaConfig: Config
    private val kafkaProperties = getKafkaProperties()

    private companion object {
        const val CLIENT_ID = "integrationTestEventPublisher"
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
        topicAdmin.createTopics(kafkaProperties, EVENTSTATE_TOPIC1_TEMPLATE)

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
        topicAdmin.createTopics(kafkaProperties, EVENTSTATE_TOPIC2_TEMPLATE)

        val onNextLatch1 = CountDownLatch(10)
        val stateEventSub1 = subscriptionFactory.createStateAndEventSubscription(
            SubscriptionConfig("$EVENT_TOPIC2-group", EVENT_TOPIC2, 1),
            TestStateEventProcessor(onNextLatch1, true, EVENTSTATE_OUTPUT2),
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
}
