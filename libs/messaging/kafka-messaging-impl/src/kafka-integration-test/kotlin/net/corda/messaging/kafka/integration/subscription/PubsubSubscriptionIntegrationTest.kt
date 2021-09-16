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
import net.corda.messaging.kafka.integration.TopicTemplates
import net.corda.messaging.kafka.integration.getRecords
import net.corda.messaging.kafka.integration.processors.TestPubsubProcessor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
class PubsubSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private lateinit var kafkaConfig: Config

    private companion object {
        const val CLIENT_ID = "integrationTestPubsubPublisher"
        const val PUBSUB_TOPIC1 = "PubsubTopic1"
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
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    fun `start pubsub subscription and publish records`() {
        val latch = CountDownLatch(1)
        val pubsubSub = subscriptionFactory.createPubSubSubscription(
            SubscriptionConfig("pubsub2", PUBSUB_TOPIC1),
            TestPubsubProcessor(latch),
            null,
            kafkaConfig
        )
        pubsubSub.start()

        publisherConfig = PublisherConfig(CLIENT_ID + PUBSUB_TOPIC1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)

        while (latch.count > 0) {
            publisher.publish(getRecords(PUBSUB_TOPIC1, 10, 2))
        }

        publisher.close()
        pubsubSub.stop()
    }
}
