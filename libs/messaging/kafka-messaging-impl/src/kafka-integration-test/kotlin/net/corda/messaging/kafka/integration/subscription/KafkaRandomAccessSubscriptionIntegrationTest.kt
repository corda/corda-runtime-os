package net.corda.messaging.kafka.integration.subscription

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.integration.IntegrationTestProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
class KafkaRandomAccessSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private lateinit var kafkaConfig: Config

    private companion object {
        const val CLIENT_ID = "publisherId"
        //automatically created topics
        const val TOPIC = "test.topic"
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @BeforeEach
    fun beforeEach() {
        kafkaConfig = ConfigFactory.empty()
            .withValue(IntegrationTestProperties.KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(IntegrationTestProperties.BOOTSTRAP_SERVERS_VALUE))
            .withValue(IntegrationTestProperties.TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
    }

    @Test
    fun `random access subscription can successfully retrieve records at specific partition and offset`() {
        publisherConfig = PublisherConfig(CLIENT_ID)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        val randomAccessSub = subscriptionFactory.createRandomAccessSubscription<String, String>(
            SubscriptionConfig("group-1", TOPIC, 1),
            kafkaConfig
        )
        randomAccessSub.start()

        val records = (1..100).map { Record(TOPIC, "key-$it", DemoRecord(it)) }
        // TODO - this can be refactored to use publisher.publishToPartition once it's implemented.
        val futures = publisher.publish(records)
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        publisher.close()

        randomAccessSub.use {
            val record = it.getRecord(1, 1)
            assertThat(record).isNotNull

            assertThat(it.getRecord(1, 300)).isNull()
        }
    }

}