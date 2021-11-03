package net.corda.messaging.kafka.integration.subscription

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
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.COMPACTED_TOPIC1
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.COMPACTED_TOPIC1_TEMPLATE
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX
import net.corda.messaging.kafka.integration.getDemoRecords
import net.corda.messaging.kafka.integration.getKafkaProperties
import net.corda.messaging.kafka.integration.processors.TestCompactedProcessor
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
class CompactedSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private lateinit var kafkaConfig: SmartConfig
    private val kafkaProperties = getKafkaProperties()

    private companion object {
        const val CLIENT_ID = "integrationTestCompactedPublisher"
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
    fun `create compacted topic, publish records, start compacted sub, publish again`() {
        topicAdmin.createTopics(kafkaProperties, COMPACTED_TOPIC1_TEMPLATE)

        publisherConfig = PublisherConfig(CLIENT_ID + COMPACTED_TOPIC1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        publisher.publish(getDemoRecords(COMPACTED_TOPIC1, 1, 5)).forEach { it.get() }

        val onNextLatch = CountDownLatch(5)
        val snapshotLatch = CountDownLatch(1)
        val compactedSub = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig("$COMPACTED_TOPIC1-group", COMPACTED_TOPIC1, 1),
            TestCompactedProcessor(snapshotLatch, onNextLatch),
            kafkaConfig
        )
        compactedSub.start()

        assertTrue(snapshotLatch.await(10, TimeUnit.SECONDS))
        assertThat(onNextLatch.count).isEqualTo(5)
        publisher.publish(getDemoRecords(COMPACTED_TOPIC1, 1, 5)).forEach { it.get() }
        publisher.close()
        assertTrue(onNextLatch.await(5, TimeUnit.SECONDS))
        assertThat(snapshotLatch.count).isEqualTo(0)

        compactedSub.stop()
    }
}
