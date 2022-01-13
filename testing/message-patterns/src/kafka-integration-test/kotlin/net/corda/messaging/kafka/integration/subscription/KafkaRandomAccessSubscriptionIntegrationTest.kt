package net.corda.messaging.kafka.integration.subscription

import com.typesafe.config.ConfigValueFactory
import net.corda.data.demo.DemoRecord
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.integration.IntegrationTestProperties
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.RANDOM_ACCESS_TOPIC1
import net.corda.messaging.kafka.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX
import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
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
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

    @BeforeEach
    fun beforeEach() {
        kafkaConfig = SmartConfigImpl.empty()
            .withValue(
                IntegrationTestProperties.KAFKA_COMMON_BOOTSTRAP_SERVER,
                ConfigValueFactory.fromAnyRef(IntegrationTestProperties.BOOTSTRAP_SERVERS_VALUE)
            )
            .withValue(IntegrationTestProperties.TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TEST_TOPIC_PREFIX))
    }

    @Test
    fun `random access subscription can successfully retrieve records at specific partition and offset`() {
        val partition = 4
        publisherConfig = PublisherConfig(CLIENT_ID + RANDOM_ACCESS_TOPIC1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)

        val coordinator =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("randomAccessTest"))
            { event: LifecycleEvent, coordinator: LifecycleCoordinator ->
                when (event) {
                    is RegistrationStatusChangeEvent -> {
                        if (event.status == LifecycleStatus.UP) {
                            coordinator.updateStatus(LifecycleStatus.UP)
                        } else {
                            coordinator.updateStatus(LifecycleStatus.DOWN)
                        }
                    }
                }
            }
        coordinator.start()

        val randomAccessSub = subscriptionFactory.createRandomAccessSubscription(
            SubscriptionConfig("group-1", RANDOM_ACCESS_TOPIC1, 1),
            kafkaConfig,
            String::class.java,
            DemoRecord::class.java
        )
        coordinator.followStatusChangesByName(setOf(randomAccessSub.subscriptionName))
        randomAccessSub.start()

        eventually(duration = 5.seconds, waitBetween = 200.millis) {
            Assertions.assertEquals(LifecycleStatus.UP, coordinator.status)
        }

        val records = (1..10).map { partition to Record(RANDOM_ACCESS_TOPIC1, "key-$it", DemoRecord(it)) }
        val futures = publisher.publishToPartition(records)
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        publisher.close()

        randomAccessSub.use {
            val record = it.getRecord(4, 2)
            assertThat(record).isNotNull
            assertThat(record).isEqualTo(records[2].second)

            assertThat(it.getRecord(4, 100)).isNull()
        }

        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            Assertions.assertEquals(LifecycleStatus.DOWN, coordinator.status)
        }
        coordinator.stop()
    }
}