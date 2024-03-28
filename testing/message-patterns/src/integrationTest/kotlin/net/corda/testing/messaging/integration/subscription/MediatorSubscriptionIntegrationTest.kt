package net.corda.testing.messaging.integration.subscription


import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.messaging.topic.utils.TopicUtils
import net.corda.libs.messaging.topic.utils.factory.TopicUtilsFactory
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.RoutingDestination
import net.corda.messaging.api.mediator.RoutingDestination.Companion.routeTo
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.EventMediatorConfigBuilder
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactoryFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactoryFactory
import net.corda.messaging.api.mediator.factory.MultiSourceEventMediatorFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.StateManagerConfig
import net.corda.testing.messaging.integration.IntegrationTestProperties.Companion.TEST_CONFIG
import net.corda.testing.messaging.integration.TopicTemplates.Companion.MEDIATOR_TOPIC1
import net.corda.testing.messaging.integration.TopicTemplates.Companion.MEDIATOR_TOPIC1_OUTPUT
import net.corda.testing.messaging.integration.TopicTemplates.Companion.MEDIATOR_TOPIC1_OUTPUT_TEMPLATE
import net.corda.testing.messaging.integration.TopicTemplates.Companion.MEDIATOR_TOPIC1_TEMPLATE
import net.corda.testing.messaging.integration.getKafkaProperties
import net.corda.testing.messaging.integration.getStringRecords
import net.corda.testing.messaging.integration.getTopicConfig
import net.corda.testing.messaging.integration.processors.TestDurableProcessorStrings
import net.corda.testing.messaging.integration.processors.TestStateEventProcessorStrings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class, BundleContextExtension::class, DBSetup::class)
class MediatorSubscriptionIntegrationTest {

    private lateinit var publisherConfig: PublisherConfig
    private lateinit var publisher: Publisher

    private companion object {
        const val CLIENT_ID = "mediatorPublisher"

        //automatically created topics
        const val THREAD_COUNT = 2
        const val GROUP_SIZE = 20
    }

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var multiSourceEventMediatorFactory: MultiSourceEventMediatorFactory

    @InjectService(timeout = 4000)
    lateinit var mediatorConsumerFactoryFactory: MediatorConsumerFactoryFactory

    @InjectService(timeout = 4000)
    lateinit var messagingClientFactoryFactory: MessagingClientFactoryFactory

    @InjectService(timeout = 4000)
    lateinit var stateManagerFactory: StateManagerFactory

    @InjectService(timeout = 4000)
    lateinit var topicUtilFactory: TopicUtilsFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    private lateinit var topicUtils: TopicUtils

    @BeforeEach
    fun beforeEach() {
        topicUtils = topicUtilFactory.createTopicUtils(getKafkaProperties())
    }

    @AfterEach
    fun afterEach() {
        topicUtils.close()
    }

    private fun buildStateManager(): StateManager {
        return stateManagerFactory.create(SmartConfigImpl.empty(), StateManagerConfig.StateType.FLOW_CHECKPOINT)
    }

    private fun buildBuilder(
        messagingConfig: SmartConfig,
        processor: StateAndEventProcessor<String, String, String>,
        outputTopic: String,
        inputTopic: String,
    ): EventMediatorConfig<String, String, String> {
        return EventMediatorConfigBuilder<String, String, String>()
            .name("FlowEventMediator")
            .messagingConfig(messagingConfig)
            .consumerFactories(
                mediatorConsumerFactoryFactory.createMessageBusConsumerFactory(
                    inputTopic, "CONSUMER_GROUP", UUID.randomUUID().toString(), messagingConfig
                )
            )
            .clientFactories(
                messagingClientFactoryFactory.createMessageBusClientFactory(
                    "MESSAGE_BUS_CLIENT", messagingConfig
                )
            )
            .messageProcessor(processor)
            .messageRouterFactory(createMessageRouterFactory(outputTopic))
            .threads(THREAD_COUNT)
            .threadName("messaging-test-mediator")
            .stateManager(buildStateManager())
            .minGroupSize(GROUP_SIZE)
            .build()
    }

    private fun createMessageRouterFactory(outputTopic: String): MessageRouterFactory {
        return MessageRouterFactory { clientFinder ->
            val messageBusClient = clientFinder.find("MESSAGE_BUS_CLIENT")
            MessageRouter { message ->
                when (message.payload) {
                    is String -> routeTo(messageBusClient, outputTopic, RoutingDestination.Type.ASYNCHRONOUS)
                    is ByteArray -> routeTo(messageBusClient, outputTopic, RoutingDestination.Type.ASYNCHRONOUS)
                    is Any -> routeTo(messageBusClient, outputTopic, RoutingDestination.Type.ASYNCHRONOUS)
                    else -> throw IllegalStateException("No route defined for message $message")
                }
            }
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `publish 50 records to an input topic and verify the processor is called and async outputs are sent`() {
        topicUtils.createTopics(getTopicConfig(MEDIATOR_TOPIC1_TEMPLATE))
        topicUtils.createTopics(getTopicConfig(MEDIATOR_TOPIC1_OUTPUT_TEMPLATE))

        publisherConfig = PublisherConfig(CLIENT_ID + MEDIATOR_TOPIC1, false)
        publisher = publisherFactory.createPublisher(publisherConfig, TEST_CONFIG)
        publisher.publish(getStringRecords(MEDIATOR_TOPIC1, 50, 1, "mediatorTest1-"))
        publisher.close()

        val latch = CountDownLatch(50)
        val processor = TestStateEventProcessorStrings(latch, true, -1, MEDIATOR_TOPIC1)
        val builder = buildBuilder(TEST_CONFIG, processor, MEDIATOR_TOPIC1_OUTPUT, MEDIATOR_TOPIC1)

        val mediator = multiSourceEventMediatorFactory.create(builder)
        mediator.start()
        latch.await()
        mediator.close()

        val verifyLatch = CountDownLatch(50)
        val verifySub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$MEDIATOR_TOPIC1_OUTPUT-verify", MEDIATOR_TOPIC1_OUTPUT),
            TestDurableProcessorStrings(verifyLatch),
            TEST_CONFIG,
            null
        )

        verifySub.start()
        assertTrue(verifyLatch.await(30, TimeUnit.SECONDS))
        verifySub.close()
    }
}
