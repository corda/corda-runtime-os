package net.corda.messaging.integration.subscription

import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.messaging.topic.utils.TopicUtils
import net.corda.libs.messaging.topic.utils.factory.TopicUtilsFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.integration.IntegrationTestProperties.Companion.TEST_CONFIG
import net.corda.messaging.integration.TopicTemplates
import net.corda.messaging.integration.getDemoRecords
import net.corda.messaging.integration.getKafkaProperties
import net.corda.messaging.integration.getTopicConfig
import net.corda.messaging.integration.processors.TestPubsubProcessor
import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
class PubSubSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher

    private companion object {
        const val CLIENT_ID = "integrationTestPubSubPublisher"
        const val PUBSUB_TOPIC1 = "PubSubTopic1"
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

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
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `start pubsub subscription and publish records`() {
        topicUtils.createTopics(getTopicConfig(TopicTemplates.PUBSUB_TOPIC1_TEMPLATE))

        val latch = CountDownLatch(1)
        val coordinator =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("pubSubTest"))
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

        val processor = TestPubsubProcessor(latch)
        val pubsubSub = subscriptionFactory.createPubSubSubscription(
            SubscriptionConfig("pubSub1", PUBSUB_TOPIC1),
            processor,
            TEST_CONFIG
        )
        coordinator.followStatusChangesByName(setOf(pubsubSub.subscriptionName))
        pubsubSub.start()

        eventually(duration = 5.seconds, waitBetween = 200.millis) {
            assertEquals(LifecycleStatus.UP, coordinator.status)
        }

        publisherConfig = PublisherConfig(CLIENT_ID + PUBSUB_TOPIC1)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)

        while (latch.count > 0) {
            publisher.publish(getDemoRecords(PUBSUB_TOPIC1, 10, 2))
        }

        publisher.close()
        pubsubSub.close()

        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertEquals(LifecycleStatus.DOWN, coordinator.status)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `start pubsub subscription and publish records with asynchronus processor`() {
        topicUtils.createTopics(getTopicConfig(TopicTemplates.PUBSUB_TOPIC1_TEMPLATE))

        val latch = CountDownLatch(2)
        val coordinator =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("anotherPubSubTest"))
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

        val processor = TestPubsubProcessor(latch, completeFuture = false)
        val pubsubSub = subscriptionFactory.createPubSubSubscription(
            SubscriptionConfig("pubSub2", PUBSUB_TOPIC1),
            processor,
            TEST_CONFIG
        )
        coordinator.followStatusChangesByName(setOf(pubsubSub.subscriptionName))
        pubsubSub.start()

        eventually(duration = 5.seconds, waitBetween = 200.millis) {
            assertEquals(LifecycleStatus.UP, coordinator.status)
        }

        publisherConfig = PublisherConfig(CLIENT_ID + PUBSUB_TOPIC1)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)

        publisher.publish(getDemoRecords(PUBSUB_TOPIC1, 1, 1))
        var firstFuture: CompletableFuture<Unit>? = null
        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            firstFuture = processor.future.get()
            assertThat(firstFuture).isNotNull
        }

        publisher.publish(getDemoRecords(PUBSUB_TOPIC1, 1, 1))
        var secondFuture: CompletableFuture<Unit>? = null
        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertThat(latch.count).isEqualTo(1)
        }
        firstFuture!!.complete(Unit)

        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            secondFuture = processor.future.get()
            assertThat(secondFuture).isNotSameAs(firstFuture)
        }

        secondFuture!!.complete(Unit)
        latch.await()

        publisher.close()
        pubsubSub.close()

        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertEquals(LifecycleStatus.DOWN, coordinator.status)
        }
    }
}
