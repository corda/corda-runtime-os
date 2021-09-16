package net.corda.messaging.kafka.integration.subscription

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.integration.IntegrationTestProperties
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX
import net.corda.messaging.kafka.integration.getDemoRecords
import net.corda.messaging.kafka.integration.processors.TestEventLogProcessor
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
class KafkaEventLogSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private lateinit var kafkaConfig: Config

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

    @BeforeEach
    fun beforeEach() {
        kafkaConfig = ConfigFactory.empty()
            .withValue(IntegrationTestProperties.KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(IntegrationTestProperties.BOOTSTRAP_SERVERS_VALUE))
            .withValue(IntegrationTestProperties.TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TEST_TOPIC_PREFIX))
    }

    @Test
    fun `asynch publish records and then start durable subscription`() {
        publisherConfig = PublisherConfig(CLIENT_ID + TOPIC1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        val futures = publisher.publish(getDemoRecords(TOPIC1, 5, 2))
        Assertions.assertThat(futures.size).isEqualTo(10)
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        publisher.close()

        val latch = CountDownLatch(10)
        val eventLogSub = subscriptionFactory.createEventLogSubscription(
            SubscriptionConfig("$TOPIC1-group", TOPIC1, 1),
            TestEventLogProcessor(latch),
            kafkaConfig,
            null
        )
        eventLogSub.start()

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        eventLogSub.stop()
    }


    @Test
    fun `transactional publish records, start two durable subscription, stop subs, publish again and start subs`() {
        publisherConfig = PublisherConfig(CLIENT_ID + TOPIC2, 1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        val futures = publisher.publish(getDemoRecords(TOPIC2, 5, 2))
        Assertions.assertThat(futures.size).isEqualTo(1)
        futures[0].get()

        val latch = CountDownLatch(30)
        val eventLogSub1 = subscriptionFactory.createEventLogSubscription(
            SubscriptionConfig("$TOPIC2-group", TOPIC2, 1),
            TestEventLogProcessor(latch),
            kafkaConfig,
            null
        )
        val eventLogSub2 = subscriptionFactory.createEventLogSubscription(
            SubscriptionConfig("$TOPIC2-group", TOPIC2, 2),
            TestEventLogProcessor(latch),
            kafkaConfig,
            null
        )

        eventLogSub1.start()
        eventLogSub2.start()

        eventLogSub1.stop()
        eventLogSub2.stop()

        publisher.publish(getDemoRecords(TOPIC2, 10, 2)).forEach { it.get() }

        eventLogSub1.start()
        eventLogSub2.start()
        assertTrue(latch.await(20, TimeUnit.SECONDS))
        eventLogSub1.stop()
        eventLogSub2.stop()
        publisher.close()
    }

}