package net.corda.messaging.integration.publisher

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.integration.TopicTemplates.Companion.PUBLISHER_TEST_DURABLE_TOPIC1
import net.corda.messaging.integration.TopicTemplates.Companion.PUBLISHER_TEST_DURABLE_TOPIC2
import net.corda.messaging.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX
import net.corda.messaging.integration.getDemoRecords
import net.corda.messaging.integration.processors.TestDurableProcessor
import net.corda.messaging.integration.util.DBSetup
import net.corda.v5.base.concurrent.getOrThrow
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
class PublisherIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private lateinit var kafkaConfig: SmartConfig

    private companion object {
        const val CLIENT_ID = "durableTestDurablePublisher"

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup() {
            val bundle = FrameworkUtil.getBundle(PublisherIntegrationTest::class.java)
            val isDBTest =
                bundle.bundleContext.bundles.find { it.symbolicName.contains("db-message-bus-impl") } != null

            if (isDBTest) {
                DBSetup.setupEntities("DB Producer for $CLIENT_ID-producer-0")
            }
        }

        @Suppress("unused")
        @AfterAll
        @JvmStatic
        fun done() {
            DBSetup.close()
        }
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @BeforeEach
    fun beforeEach() {
        kafkaConfig = SmartConfigImpl.empty()
            .withValue(
                net.corda.messaging.integration.IntegrationTestProperties.KAFKA_COMMON_BOOTSTRAP_SERVER,
                ConfigValueFactory.fromAnyRef(net.corda.messaging.integration.IntegrationTestProperties.BOOTSTRAP_SERVERS_VALUE)
            )
            .withValue(net.corda.messaging.integration.IntegrationTestProperties.TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TEST_TOPIC_PREFIX))
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `publisher can publish records to partitions non-transactionally successfully`() {
        publisherConfig = PublisherConfig(CLIENT_ID)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)

        val recordsWithPartitions = getDemoRecords(PUBLISHER_TEST_DURABLE_TOPIC1, 5, 2).map { 1 to it }
        val futures = publisher.publishToPartition(recordsWithPartitions)
        futures.map { it.getOrThrow() }
        publisher.close()

        val latch = CountDownLatch(recordsWithPartitions.size)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$PUBLISHER_TEST_DURABLE_TOPIC1-group", PUBLISHER_TEST_DURABLE_TOPIC1, 1),
            TestDurableProcessor(latch),
            kafkaConfig,
            null
        )
        durableSub.start()

        Assertions.assertTrue(latch.await(20, TimeUnit.SECONDS))
        durableSub.stop()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.DAYS)
    fun `publisher can publish records to partitions transactionally successfully`() {
        publisherConfig = PublisherConfig(CLIENT_ID, 1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)

        val recordsWithPartitions = getDemoRecords(PUBLISHER_TEST_DURABLE_TOPIC1, 5, 2).map { 1 to it }
        val futures = publisher.publishToPartition(recordsWithPartitions)
        futures.map { it.getOrThrow() }
        publisher.close()

        val latch = CountDownLatch(recordsWithPartitions.size)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$PUBLISHER_TEST_DURABLE_TOPIC1-group", PUBLISHER_TEST_DURABLE_TOPIC1, 2),
            TestDurableProcessor(latch),
            kafkaConfig,
            null
        )
        durableSub.start()

        Assertions.assertTrue(latch.await(20, TimeUnit.SECONDS))
        durableSub.stop()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `publisher can publish records to partitions transactionally successfully from multiple threads`() {
        publisherConfig = PublisherConfig(CLIENT_ID, 2)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)

        val recordsWithPartitions = getDemoRecords(PUBLISHER_TEST_DURABLE_TOPIC2, 5, 2).map { 1 to it }
        val barrier = CyclicBarrier(3)
        val thread1 = Thread {
            barrier.await()
            val futures = publisher.publishToPartition(recordsWithPartitions)
            futures.map { it.getOrThrow() }
            barrier.await()
        }
        val thread2 = Thread {
            barrier.await()
            val futures = publisher.publishToPartition(recordsWithPartitions)
            futures.map { it.getOrThrow() }
            barrier.await()
        }
        thread1.start()
        thread2.start()
        barrier.await()
        barrier.await()
        publisher.close()

        val latch = CountDownLatch(recordsWithPartitions.size * 2)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$PUBLISHER_TEST_DURABLE_TOPIC2-group", PUBLISHER_TEST_DURABLE_TOPIC2, 1),
            TestDurableProcessor(latch),
            kafkaConfig,
            null
        )
        durableSub.start()

        Assertions.assertTrue(latch.await(20, TimeUnit.SECONDS))
        durableSub.stop()
    }
}
