package net.corda.messaging.kafka.integration.subscription

import com.typesafe.config.ConfigValueFactory
import net.corda.data.demo.DemoRecord
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.integration.IntegrationTestProperties
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX
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
    private lateinit var kafkaConfig: SmartConfig

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
        kafkaConfig = SmartConfigImpl.empty()
            .withValue(IntegrationTestProperties.KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(IntegrationTestProperties.BOOTSTRAP_SERVERS_VALUE))
            .withValue(IntegrationTestProperties.TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TEST_TOPIC_PREFIX))
    }

    @Test
    fun `random access subscription can successfully retrieve records at specific partition and offset`() {
        val partition = 4
        publisherConfig = PublisherConfig(CLIENT_ID + TOPIC)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        val randomAccessSub = subscriptionFactory.createRandomAccessSubscription(
            SubscriptionConfig("group-1", TOPIC, 1),
            kafkaConfig,
            String::class.java,
            DemoRecord::class.java
        )
        randomAccessSub.start()

        val records = (1..10).map { partition to Record(TOPIC, "key-$it", DemoRecord(it)) }
        val futures = publisher.publishToPartition(records)
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        publisher.close()

        randomAccessSub.use {
            val record = it.getRecord(4, 2)
            assertThat(record).isNotNull
            assertThat(record).isEqualTo(records[2].second)

            assertThat(it.getRecord(4, 100)).isNull()
        }
    }

}