package net.corda.messaging.kafka.integration.subscription

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.BOOTSTRAP_SERVERS_VALUE
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.KAFKA_COMMON_BOOTSTRAP_SERVER
import net.corda.messaging.kafka.integration.IntegrationTestProperties.Companion.TOPIC_PREFIX
import net.corda.messaging.kafka.integration.getRecords
import net.corda.messaging.kafka.integration.processors.TestDurableProcessor
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
    private lateinit var kafkaConfig: Config

    private companion object {
        const val CLIENT_ID = "durableTestDurablePublisher"
        //automatically created topics
        const val DURABLE_TOPIC1 = "DurableTopic1"
        const val DURABLE_TOPIC2 = "DurableTopic2"
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @BeforeEach
    fun beforeEach() {
        kafkaConfig = ConfigFactory.empty()
            .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(BOOTSTRAP_SERVERS_VALUE))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
    }

    @Test
    fun `asynch publish records and then start durable subscription`() {
        publisherConfig = PublisherConfig(CLIENT_ID)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        val futures = publisher.publish(getRecords(DURABLE_TOPIC1, 5, 2))
        assertThat(futures.size).isEqualTo(10)
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        publisher.close()

        val latch = CountDownLatch(10)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC1-group", DURABLE_TOPIC1, 1),
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
        publisherConfig = PublisherConfig(CLIENT_ID, 1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        val futures = publisher.publish(getRecords(DURABLE_TOPIC2, 5, 2))
        assertThat(futures.size).isEqualTo(1)
        futures[0].get()

        val latch = CountDownLatch(30)
        val durableSub1 = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC2-group", DURABLE_TOPIC2, 1),
            TestDurableProcessor(latch),
            kafkaConfig,
            null
        )
        val durableSub2 = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$DURABLE_TOPIC2-group", DURABLE_TOPIC2, 2),
            TestDurableProcessor(latch),
            kafkaConfig,
            null
        )

        durableSub1.start()
        durableSub2.start()

        durableSub1.stop()
        durableSub2.stop()

        publisher.publish(getRecords(DURABLE_TOPIC2, 10, 2)).forEach { it.get() }

        durableSub1.start()
        durableSub2.start()
        assertTrue(latch.await(20, TimeUnit.SECONDS))
        durableSub1.stop()
        durableSub2.stop()
        publisher.close()
    }
}
