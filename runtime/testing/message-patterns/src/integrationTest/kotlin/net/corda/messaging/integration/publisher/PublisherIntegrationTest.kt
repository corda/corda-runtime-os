package net.corda.messaging.integration.publisher

import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.messaging.topic.utils.TopicUtils
import net.corda.libs.messaging.topic.utils.factory.TopicUtilsFactory
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.integration.IntegrationTestProperties.Companion.TEST_CONFIG
import net.corda.messaging.integration.TopicTemplates.Companion.PUBLISHER_TEST_DURABLE_TOPIC1
import net.corda.messaging.integration.TopicTemplates.Companion.PUBLISHER_TEST_DURABLE_TOPIC1_TEMPLATE
import net.corda.messaging.integration.TopicTemplates.Companion.PUBLISHER_TEST_DURABLE_TOPIC2
import net.corda.messaging.integration.TopicTemplates.Companion.PUBLISHER_TEST_DURABLE_TOPIC2_TEMPLATE
import net.corda.messaging.integration.TopicTemplates.Companion.PUBLISHER_TEST_DURABLE_TOPIC3
import net.corda.messaging.integration.TopicTemplates.Companion.PUBLISHER_TEST_DURABLE_TOPIC3_TEMPLATE
import net.corda.messaging.integration.getDemoRecords
import net.corda.messaging.integration.getKafkaProperties
import net.corda.messaging.integration.getTopicConfig
import net.corda.messaging.integration.processors.TestDurableProcessor
import net.corda.utilities.concurrent.getOrThrow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
class PublisherIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig

    private companion object {
        const val CLIENT_ID = "durableTestDurablePublisher"
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var topicUtilFactory: TopicUtilsFactory

    private lateinit var topicUtils: TopicUtils

    @BeforeEach
    fun beforeEach() {
        topicUtils = topicUtilFactory.createTopicUtils(getKafkaProperties())
    }

    @AfterEach
    fun afterEach() {
        topicUtils.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `publisher can publish records to partitions non-transactionally successfully`() {
        topicUtils.createTopics(getTopicConfig(PUBLISHER_TEST_DURABLE_TOPIC1_TEMPLATE))
        publisherConfig = PublisherConfig("$CLIENT_ID.$PUBLISHER_TEST_DURABLE_TOPIC1")
        val publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)

        val recordsWithPartitions = getDemoRecords(PUBLISHER_TEST_DURABLE_TOPIC1, 5, 2).map { 1 to it }
        val futures = publisher.publishToPartition(recordsWithPartitions)
        futures.map { it.getOrThrow() }
        publisher.close()

        val latch = CountDownLatch(recordsWithPartitions.size)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$PUBLISHER_TEST_DURABLE_TOPIC1-group", PUBLISHER_TEST_DURABLE_TOPIC1),
            TestDurableProcessor(latch),
            TEST_CONFIG,
            null
        )
        durableSub.start()

        Assertions.assertTrue(latch.await(20, TimeUnit.SECONDS))
        durableSub.close()
    }
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `publisher can publish records to partitions transactionally successfully`() {
        topicUtils.createTopics(getTopicConfig(PUBLISHER_TEST_DURABLE_TOPIC2_TEMPLATE))
        publisherConfig = PublisherConfig("$CLIENT_ID.$PUBLISHER_TEST_DURABLE_TOPIC2", true)
        val publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)

        val recordsWithPartitions = getDemoRecords(PUBLISHER_TEST_DURABLE_TOPIC2, 5, 2).map { 1 to it }
        val futures = publisher.publishToPartition(recordsWithPartitions)
        futures.map { it.getOrThrow() }
        publisher.close()

        val latch = CountDownLatch(recordsWithPartitions.size)
        val durableSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$PUBLISHER_TEST_DURABLE_TOPIC2-group", PUBLISHER_TEST_DURABLE_TOPIC2),
            TestDurableProcessor(latch),
            TEST_CONFIG,
            null
        )
        durableSub.start()

        Assertions.assertTrue(latch.await(20, TimeUnit.SECONDS))
        durableSub.close()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `publisher can publish records to partitions transactionally successfully from multiple threads`() {
        topicUtils.createTopics(getTopicConfig(PUBLISHER_TEST_DURABLE_TOPIC3_TEMPLATE))
        publisherConfig = PublisherConfig("$CLIENT_ID.$PUBLISHER_TEST_DURABLE_TOPIC3", true)
        val publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)

        val recordsWithPartitions = getDemoRecords(PUBLISHER_TEST_DURABLE_TOPIC3, 5, 2).map { 1 to it }
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
            SubscriptionConfig("$PUBLISHER_TEST_DURABLE_TOPIC3-group", PUBLISHER_TEST_DURABLE_TOPIC3),
            TestDurableProcessor(latch),
            TEST_CONFIG,
            null
        )
        durableSub.start()

        Assertions.assertTrue(latch.await(20, TimeUnit.SECONDS))
        durableSub.close()
    }
}
