package net.corda.messaging.integration.subscription

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
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
import net.corda.messaging.integration.IntegrationTestProperties.Companion.BOOTSTRAP_SERVERS_VALUE
import net.corda.messaging.integration.IntegrationTestProperties.Companion.KAFKA_COMMON_BOOTSTRAP_SERVER
import net.corda.messaging.integration.IntegrationTestProperties.Companion.TOPIC_PREFIX
import net.corda.messaging.integration.TopicTemplates.Companion.COMPACTED_TOPIC1
import net.corda.messaging.integration.TopicTemplates.Companion.COMPACTED_TOPIC1_TEMPLATE
import net.corda.messaging.integration.TopicTemplates.Companion.COMPACTED_TOPIC2
import net.corda.messaging.integration.TopicTemplates.Companion.COMPACTED_TOPIC2_TEMPLATE
import net.corda.messaging.integration.TopicTemplates.Companion.TEST_TOPIC_PREFIX
import net.corda.messaging.integration.getDemoRecords
import net.corda.messaging.integration.getKafkaProperties
import net.corda.messaging.integration.getStringRecords
import net.corda.messaging.integration.processors.TestCompactedProcessor
import net.corda.messaging.integration.publisher.PublisherIntegrationTest
import net.corda.messaging.integration.util.DBSetup
import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.framework.FrameworkUtil
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class)
class CompactedSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher
    private lateinit var kafkaConfig: SmartConfig
    private companion object {
        const val CLIENT_ID = "integrationTestCompactedPublisher"

        private var compactedTopic1Config = ConfigFactory.parseString(COMPACTED_TOPIC1_TEMPLATE)
        private var compactedTopic2Config = ConfigFactory.parseString(COMPACTED_TOPIC2_TEMPLATE)

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup() {
            val bundle = FrameworkUtil.getBundle(PublisherIntegrationTest::class.java)
            val isDBTest =
                bundle.bundleContext.bundles.find { it.symbolicName.contains("db-message-bus-impl") } != null

            if (isDBTest) {
                DBSetup.setupEntities("DB Consumer for ${CLIENT_ID}-consumer-0")
                // Dodgy remove prefix for DB code
                compactedTopic1Config = ConfigFactory.parseString(
                    COMPACTED_TOPIC1_TEMPLATE.replace(TEST_TOPIC_PREFIX,"")
                )
                compactedTopic2Config = ConfigFactory.parseString(
                    COMPACTED_TOPIC2_TEMPLATE.replace(TEST_TOPIC_PREFIX,"")
                )
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

    @InjectService(timeout = 4000)
    lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

    @InjectService(timeout = 4000)
    lateinit var topicUtilFactory: TopicUtilsFactory

    private lateinit var topicUtils: TopicUtils

    @BeforeEach
    fun beforeEach() {
        topicUtils = topicUtilFactory.createTopicUtils(getKafkaProperties())
        kafkaConfig = SmartConfigImpl.empty()
            .withValue(KAFKA_COMMON_BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(BOOTSTRAP_SERVERS_VALUE))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(TEST_TOPIC_PREFIX))
    }

    @Disabled
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `create compacted topic, publish records, start compacted sub, publish again`() {
        topicUtils.createTopics(compactedTopic1Config)
//        topicAdmin.createTopics(kafkaProperties, COMPACTED_TOPIC1_TEMPLATE)

        publisherConfig = PublisherConfig(CLIENT_ID + COMPACTED_TOPIC1)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        publisher.publish(getDemoRecords(COMPACTED_TOPIC1, 1, 5)).forEach { it.get() }

        val coordinator =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("compactedTest"))
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

        val onNextLatch = CountDownLatch(5)
        val snapshotLatch = CountDownLatch(1)
        val compactedSub = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig("$COMPACTED_TOPIC1-group", COMPACTED_TOPIC1, 1),
            TestCompactedProcessor(snapshotLatch, onNextLatch),
            kafkaConfig
        )
        coordinator.followStatusChangesByName(setOf(compactedSub.subscriptionName))
        compactedSub.start()

        eventually(duration = 5.seconds, waitBetween = 200.millis) {
            assertEquals(LifecycleStatus.UP, coordinator.status)
        }
        assertTrue(snapshotLatch.await(10, TimeUnit.SECONDS))
        assertThat(onNextLatch.count).isEqualTo(5)
        publisher.publish(getDemoRecords(COMPACTED_TOPIC1, 1, 5)).forEach { it.get() }
        publisher.close()
        assertTrue(onNextLatch.await(5, TimeUnit.SECONDS))
        assertThat(snapshotLatch.count).isEqualTo(0)

        compactedSub.stop()
        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertEquals(LifecycleStatus.DOWN, coordinator.status)
        }
        coordinator.stop()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `create compacted topic, publish wrong records, start compacted sub`() {
        topicUtils.createTopics(compactedTopic2Config)
//        topicAdmin.createTopics(kafkaProperties, COMPACTED_TOPIC2_TEMPLATE)

        publisherConfig = PublisherConfig(CLIENT_ID + COMPACTED_TOPIC2)
        publisher = publisherFactory.createPublisher(publisherConfig, kafkaConfig)
        publisher.publish(getStringRecords(COMPACTED_TOPIC2, 1, 5)).forEach { it.get() }

        val coordinator =
            lifecycleCoordinatorFactory.createCoordinator(LifecycleCoordinatorName("compactedTest2"))
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

        val onNextLatch = CountDownLatch(5)
        val snapshotLatch = CountDownLatch(1)
        val compactedSub = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig("$COMPACTED_TOPIC2-group", COMPACTED_TOPIC2, 1),
            TestCompactedProcessor(snapshotLatch, onNextLatch),
            kafkaConfig
        )
        coordinator.followStatusChangesByName(setOf(compactedSub.subscriptionName))
        compactedSub.start()

        eventually(duration = 10.seconds, waitBetween = 200.millis) {
            assertEquals(LifecycleStatus.UP, coordinator.status)
        }
        assertTrue(snapshotLatch.await(10, TimeUnit.SECONDS))
        assertThat(onNextLatch.count).isEqualTo(5)

        publisher.close()
        compactedSub.stop()
        eventually(duration = 5.seconds, waitBetween = 10.millis, waitBefore = 0.millis) {
            assertEquals(LifecycleStatus.DOWN, coordinator.status)
        }
        coordinator.stop()
    }
}
