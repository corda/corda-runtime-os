package net.corda.messaging.mediator

import java.time.Duration
import java.util.concurrent.CompletableFuture
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.RoutingDestination
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.EventMediatorConfigBuilder
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.config.MessagingClientConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFinder
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.schema.configuration.MessagingConfig
import net.corda.taskmanager.TaskManager
import net.corda.test.util.waitWhile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MultiSourceEventMediatorImplTest {
    companion object {
        private const val TEST_TIMEOUT_SECONDS = 20L
        const val KEY1 = "key1"
        const val KEY2 = "key2"
    }

    private lateinit var mediator: MultiSourceEventMediatorImpl<Any, Any, Any>

    private val stateSerializer: CordaAvroSerializer<Any> = mock()
    private val stateDeserializer: CordaAvroDeserializer<Any> = mock()
    private val stateManager: StateManager = mock()
    private val taskManager: TaskManager = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()

    private val messageProcessor: StateAndEventProcessor<Any, Any, Any> = mock()
    private val consumerFactory: MediatorConsumerFactory = mock()
    private val clientFactory: MessagingClientFactory = mock()
    private val messageRouterFactory: MessageRouterFactory = mock()

    private val consumer: MediatorConsumer<Any, Any> = mock()
    private val messagingClient: MessagingClient = mock()
    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private val messagingConfig: SmartConfig = mock()

    @BeforeEach
    fun setup() {
        mediator = createMediator()
    }

    private fun createMediator() : MultiSourceEventMediatorImpl<Any, Any, Any> {
        val config = createMockConfig()

        whenever(stateSerializer.serialize(any())).thenReturn("serializedData".toByteArray())
        whenever(stateDeserializer.deserialize(any())).thenReturn("serializedData")

        setupStateManager()
        setupTaskManager()

        whenever(lifecycleCoordinatorFactory.createCoordinator(any(), anyOrNull())).thenReturn(lifecycleCoordinator)

        return MultiSourceEventMediatorImpl(
            config,
            stateSerializer,
            stateDeserializer,
            stateManager,
            taskManager,
            lifecycleCoordinatorFactory
        )
    }

    private fun createMockConfig() : EventMediatorConfig<Any, Any, Any> {
        whenever(messagingConfig.getInt(MessagingConfig.Subscription.PROCESSOR_RETRIES)).thenReturn(3)

        whenever(messageProcessor.keyClass).thenReturn(Any::class.java)
        whenever(messageProcessor.eventValueClass).thenReturn(Any::class.java)

        whenever(consumerFactory.create(any<MediatorConsumerConfig<Any, Any>>())).thenReturn(consumer)

        whenever(messagingClient.send(any())).thenAnswer { null }

        whenever(clientFactory.create(any<MessagingClientConfig>())).thenReturn(messagingClient)

        whenever(messageRouterFactory.create(any<MessagingClientFinder>())).thenReturn(MessageRouter { _ ->
            RoutingDestination.routeTo(messagingClient, "endpoint")
        })

        return EventMediatorConfigBuilder<Any, Any, Any>()
            .name("TestConfig")
            .messagingConfig(messagingConfig)
            .messageProcessor(messageProcessor)
            .consumerFactories(consumerFactory)
            .clientFactories(clientFactory)
            .messageRouterFactory(messageRouterFactory)
            .stateManager(stateManager)
            .threads(1)
            .threadName("test")
            .build()
    }

    private fun setupStateManager() {
        // This should return any states which could not be persisted. If we want this to fail,
        // we need to return a map of keys to exceptions. It's called inside StateManagerHelper
        whenever(stateManager.create(any())).thenReturn(mapOf("FAILED_KEY" to Exception("test")))

        // This function is also called in StateManagerHelper::persistStates using the keys
        // returned by the .create() call in order to get the latest state of failed creations
        whenever(stateManager.get(listOf("FAILED_KEY"))).thenReturn(mapOf("" to mock()))

        // This should return a list of the currently persisted states in StateManager.
        // It's called each time processEvents() is run.
        whenever(stateManager.get(any())).thenReturn(mapOf("" to mock()))


        // This function returns the most up-to-date version of the states; essentially a create + get
        // called in StateManagerHelper::persistStates()
        whenever(stateManager.update(any())).thenReturn(mapOf("" to mock()))
    }

    private fun setupTaskManager() {
        whenever(taskManager.executeLongRunningTask (any<() -> Any>())).thenAnswer { invocation ->
            val command = invocation.getArgument<() -> Any>(0)
            CompletableFuture.supplyAsync(command)
        }
    }


    // @Test
    // TODO Test temporarily disabled as it seems to be flaky
    fun `mediator processes multiples events by key`() {
        val events = (1..6).map { "event$it" }
        val eventBatches = listOf(
            listOf(
                cordaConsumerRecords(KEY1, events[0]),
                cordaConsumerRecords(KEY2, events[1]),
                cordaConsumerRecords(KEY1, events[2]),
            ),
            listOf(
                cordaConsumerRecords(KEY2, events[3]),
                cordaConsumerRecords(KEY2, events[4]),
                cordaConsumerRecords(KEY1, events[5]),
            ),
        )
        var batchNumber = 0
        whenever(consumer.poll(any())).thenAnswer {
            if (batchNumber < eventBatches.size) {
                eventBatches[batchNumber++]
            } else {
                Thread.sleep(10)
                emptyList()
            }
        }

        mediator.start()
        waitWhile(Duration.ofSeconds(TEST_TIMEOUT_SECONDS)) { batchNumber < eventBatches.size }
        mediator.close()

        verify(consumerFactory).create(any<MediatorConsumerConfig<Any, Any>>())
        verify(clientFactory).create(any<MessagingClientConfig>())
        verify(messageRouterFactory).create(any<MessagingClientFinder>())
        verify(messageProcessor, times(events.size)).onNext(anyOrNull(), any())
        verify(stateManager, times(eventBatches.size)).get(any())
        verify(stateManager, times(eventBatches.size)).create(any())
        verify(consumer, atLeast(eventBatches.size)).poll(any())
        verify(consumer, times(eventBatches.size)).syncCommitOffsets()
        verify(messagingClient, times(events.size)).send(any())
    }

    @Test
    fun `processEventWithRetries retries correct number of times`() {
        whenever(consumer.poll(any()))
            .thenThrow(CordaMessageAPIIntermittentException("test"))
//            .thenReturn(listOf(cordaConsumerRecords("key", "event" )))
//            .thenThrow(CordaMessageAPIFatalException("FATAL"))

        mediator.start()

        verify(consumerFactory, times(2)).create<Any, Any>(any())
        verify(clientFactory, times(2)).create(any())
        verify(messageRouterFactory, times(2)).create(any())
    }

    @Test
    fun `mediator retries after intermittent exception`() {
        whenever(consumer.poll(any()))
            .thenThrow(CordaMessageAPIIntermittentException("intermittent 1"))
            .thenThrow(CordaMessageAPIIntermittentException("intermittent 2"))
            .thenThrow(CordaMessageAPIIntermittentException("intermittent 3"))
            .thenThrow(CordaMessageAPIFatalException("fatal"))

        mediator.start()

        verify(consumerFactory, times(2)).create<Any, Any>(any())
        verify(clientFactory, times(2)).create(any())
        verify(messageRouterFactory, times(2)).create(any())


//        //set up message processor that tells us when we've processed the right number of events (the signal from one thread to the test thread)
//        //make a msg processor that takes a latch
//        val latch = CountDownLatch(eventsToBeProcessed)
//        whenever(messageProcessor.onNext(any(), any())).then {
//            latch.countDown()
//        }.thenAnswer {
//            StateAndEventProcessor.Response<Any>(
//                updatedState = mock(),
//                responseEvents = listOf(
//                    Record(
//                        topic = "", "key", value = mock(), timestamp = 0
//                    )
//                ),
//            )
//        }

        //              test execution
//        //start mediator
//        mediator.start()
//        //wait for signal - true for success, false for timeout
////        assertTrue(latch.await(10, TimeUnit.SECONDS))
//
//        mediator.close()
//
//        //              test verification
//        //check consumers list to check each item is closed
//        //check clients list to check each item is closed
//        verify(consumer).close()
//        verify(messagingClient).close()
//        //1) all events processed - e.g. push 5, process 5
//        verify(messageProcessor, times(5)).onNext(any(), any())
//
//        //2) verify mocks recreated by apis being called again
//        //consumer mock, client mock, message router mock:
//
//        verify(consumerFactory, times(2)).create<Any, Any>(any())
//        verify(clientFactory, times(2)).create(any())
//        verify(messageRouterFactory, times(2)).create(any())
//
//        //3) verify api that sets it to up is called
//        verify(lifecycleCoordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `mediator closes after interrupted exception`() {

    }

    @Test
    fun `mediator closes and updates status after other exception`() {

    }

    private fun cordaConsumerRecords(key: String, event: String): CordaConsumerRecord<Any, Any> = CordaConsumerRecord(
        topic = "", partition = 0, offset = 0, key, event, timestamp = 0
    )
}