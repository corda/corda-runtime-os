package net.corda.messaging.kafka.integration.subscription

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.BOOTSTRAP_SERVERS_VALUE
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.KAFKA_COMMON_BOOTSTRAP_SERVER
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.TOPIC_PREFIX
import net.corda.messaging.kafka.integration.TopicTemplates
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.DURABLE_TOPIC1
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX
import net.corda.messaging.kafka.integration.getKafkaProperties
import net.corda.messaging.kafka.integration.getDemoRecords
import net.corda.messaging.kafka.integration.processors.TestDurableProcessor
import net.corda.messaging.kafka.properties.ConfigProperties
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.MESSAGING_KAFKA
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
class DurableSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private lateinit var kafkaConfig: SmartConfig
    private val kafkaProperties = getKafkaProperties()

    private companion object {
        const val CLIENT_ID = "durableTestDurablePublisher"
        //automatically created topics
        const val DURABLE_TOPIC2 = "DurableTopic2"
        const val DURABLE_TOPIC3 = "DurableTopic3"
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var topicAdmin: KafkaTopicAdmin

    @BeforeEach
    fun beforeEach() {
        kafkaConfig = SmartConfigImpl.empty()
            .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(BOOTSTRAP_SERVERS_VALUE))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TEST_TOPIC_PREFIX))
    }

    @Test
    fun `asynch publish records and then start 2 durable subscriptions, delay 1 sub, trigger rebalance`() {
        topicAdmin.createTopics(kafkaProperties, TopicTemplates.DURABLE_TOPIC1_TEMPLATE)

        publisherConfig = PublisherConfig(CLIENT_ID + DURABLE_TOPIC1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        val futures = publisher.publish(getDemoRecords(DURABLE_TOPIC1, 5, 3))
        assertThat(futures.size).isEqualTo(15)
        publisher.close()

        val latch = CountDownLatch(15)
        val durableSub1 = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC1-group", DURABLE_TOPIC1, 1),
            TestDurableProcessor(latch),
            kafkaConfig,
            null
        )

        val triggerRebalanceQuicklyConfig = kafkaConfig
            .withValue("$MESSAGING_KAFKA.${ConfigProperties.CONSUMER_MAX_POLL_INTERVAL}", ConfigValueFactory.fromAnyRef(1000))
        //long delay to not allow sub to to try rejoin group after rebalance
        val durableSub2 = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC1-group", DURABLE_TOPIC1, 2),
            TestDurableProcessor(latch),
            triggerRebalanceQuicklyConfig,
            null
        )
        durableSub1.start()
        durableSub2.start()

        assertTrue(latch.await(70, TimeUnit.SECONDS))
        durableSub1.stop()
        durableSub2.stop()
    }

    @Test
    fun `asynch publish records and then start durable subscription`() {
        publisherConfig = PublisherConfig(CLIENT_ID + DURABLE_TOPIC2)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        val futures = publisher.publish(getDemoRecords(DURABLE_TOPIC2, 5, 2))
        assertThat(futures.size).isEqualTo(10)
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        publisher.close()

        val latch = CountDownLatch(10)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC2-group", DURABLE_TOPIC2, 1),
            TestDurableProcessor(latch),
            kafkaConfig,
            null
        )
        durableSub.start()

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        durableSub.stop()
    }

    @Test
    fun `transactional publish records, start two durable subscription, stop subs, publish again and start subs`() {
        publisherConfig = PublisherConfig(CLIENT_ID + DURABLE_TOPIC3, 1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        val futures = publisher.publish(getDemoRecords(DURABLE_TOPIC3, 5, 2))
        assertThat(futures.size).isEqualTo(1)
        futures[0].get()

        val latch = CountDownLatch(30)
        val durableSub1 = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC3-group", DURABLE_TOPIC3, 1),
            TestDurableProcessor(latch),
            kafkaConfig,
            null
        )
        val durableSub2 = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC3-group", DURABLE_TOPIC3, 2),
            TestDurableProcessor(latch),
            kafkaConfig,
            null
        )

        durableSub1.start()
        durableSub2.start()

        durableSub1.stop()
        durableSub2.stop()

        publisher.publish(getDemoRecords(DURABLE_TOPIC3, 10, 2)).forEach { it.get() }

        durableSub1.start()
        durableSub2.start()
        assertTrue(latch.await(20, TimeUnit.SECONDS))
        durableSub1.stop()
        durableSub2.stop()
        publisher.close()
    }
}
