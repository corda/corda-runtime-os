package net.corda.messaging.integration

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.integration.processors.TestDurableProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
class CordaKafkaIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private lateinit var kafkaConfig: Config
    private val kafkaProperties = getKafkaProperties()

    private companion object {
        const val CLIENT_ID = "integrationTestPublisher"
        const val BOOTSTRAP_SERVERS_VALUE = "localhost:9092"
        const val BOOTSTRAP_SERVERS = "bootstrap.servers"
        const val TOPIC_PREFIX = "messaging.topic.prefix"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
        const val PUBLISHER_TOPIC1 = "PublisherTopic1"
        const val PUBLISHER_TOPIC_TEMPLATE = "topics = [" +
                "    {\n" +
                "         topicName = \"$PUBLISHER_TOPIC1\"\n" +
                "         numPartitions = 3\n" +
                "         replicationFactor = 3\n" +
                "     }\n" +
                "]"
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
    fun testPublish() {
        topicAdmin.createTopics(kafkaProperties, PUBLISHER_TOPIC_TEMPLATE)

        publisherConfig = PublisherConfig(CLIENT_ID)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        val futures = publisher.publish(getRecords(PUBLISHER_TOPIC1, 5, 2))
        assertThat(futures.size).isEqualTo(10)
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        publisher.close()

        val latch = CountDownLatch(10)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("durable1", PUBLISHER_TOPIC1, 1),
            TestDurableProcessor(latch),
            kafkaConfig,
            null
        )
        durableSub.start()

        latch.await(15, TimeUnit.SECONDS)
        durableSub.stop()
        assertThat(durableSub.isRunning).isEqualTo(false)
    }

    private fun getKafkaProperties(): Properties {
        val kafkaProperties = Properties()
        kafkaProperties[BOOTSTRAP_SERVERS] = BOOTSTRAP_SERVERS_VALUE
        return kafkaProperties
    }


    private fun getRecords(topic: String, recordCount: Int, keyCount: Int): List<Record<*, *>> {
        val records = mutableListOf<Record<*, *>>()
        for (i in 1..keyCount) {
            val key = "key$i"
            for (j in 1..recordCount) {
                records.add(Record(topic, key, DemoRecord(j)))
            }
        }
        return records
    }
}
