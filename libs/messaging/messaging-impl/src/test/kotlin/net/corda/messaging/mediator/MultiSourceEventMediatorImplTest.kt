package net.corda.messaging.mediator

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
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
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.MessagingConfig
import net.corda.taskmanager.TaskManager
import net.corda.test.util.waitWhile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MultiSourceEventMediatorImplTest {
    companion object {
        private const val TEST_TIMEOUT_SECONDS = 20L
        const val KEY1 = "key1"
        const val KEY2 = "key2"

        const val PROCESSOR_RETRIES = 2
    }

    private lateinit var mediator: MultiSourceEventMediatorImpl<Any, Any, Any>

    private val stateSerializer: CordaAvroSerializer<Any> = mock()
    private val stateDeserializer: CordaAvroDeserializer<Any> = mock()
    private val stateManager: StateManager = mock()
    private val taskManager: TaskManager = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()

    private val messageProcessor: StateAndEventProcessor<Any, Any, Any> = mock()
    private val mediatorConsumerFactory: MediatorConsumerFactory = mock()
    private val messagingClientFactory: MessagingClientFactory = mock()
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
        whenever(messagingConfig.getInt(MessagingConfig.Subscription.PROCESSOR_RETRIES)).thenReturn(PROCESSOR_RETRIES)

        whenever(
            messageProcessor.onNext(
                anyOrNull(),
                any()
            )
        ).thenAnswer {
            StateAndEventProcessor.Response<Any>(
                updatedState = mock(),
                responseEvents = listOf(Record(topic = "", "key", value = mock(), timestamp = 0)),
            )
        }
        whenever(messageProcessor.keyClass).thenReturn(Any::class.java)
        whenever(messageProcessor.eventValueClass).thenReturn(Any::class.java)

        whenever(mediatorConsumerFactory.create(any<MediatorConsumerConfig<Any, Any>>())).thenReturn(consumer)

        whenever(messagingClient.send(any())).thenAnswer { null }

        whenever(messagingClientFactory.create(any<MessagingClientConfig>())).thenReturn(messagingClient)

        whenever(messageRouterFactory.create(any<MessagingClientFinder>())).thenReturn(MessageRouter { _ ->
            RoutingDestination.routeTo(messagingClient, "endpoint")
        })

        return EventMediatorConfigBuilder<Any, Any, Any>()
            .name("TestConfig")
            .messagingConfig(messagingConfig)
            .messageProcessor(messageProcessor)
            .consumerFactories(mediatorConsumerFactory)
            .clientFactories(messagingClientFactory)
            .messageRouterFactory(messageRouterFactory)
            .stateManager(stateManager)
            .threads(1)
            .threadName("mediator-thread")
            .build()
            .also { whenever(it.processorRetries).thenReturn(PROCESSOR_RETRIES) }
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
        whenever(stateManager.get(any())).thenReturn(mapOf(KEY1 to mock(), KEY2 to mock()))

        // This function returns the most up-to-date version of the states; essentially a create + get
        // called in StateManagerHelper::persistStates()
        whenever(stateManager.update(any())).thenReturn(mapOf("" to mock()))
    }

    private fun setupTaskManager() {
        whenever(taskManager.executeLongRunningTask (any<() -> Any>())).thenAnswer { invocation ->
            val command = invocation.getArgument<() -> Any>(0)
            CompletableFuture.supplyAsync(command)
        }

        whenever(taskManager.executeShortRunningTask (any<() -> Any>())).thenAnswer { invocation ->
            val command = invocation.getArgument<() -> Any>(0)
            CompletableFuture.supplyAsync(command)
        }
    }

    @Test
    fun `processEventWithRetries retries correct number of times`() {
        val barrier = CyclicBarrier(2)
        whenever(consumer.close()).then {
            barrier.await()
        }

        whenever(consumer.poll(any()))
            .thenThrow(CordaMessageAPIIntermittentException("Intermittent"))

        mediator.start()

        barrier.await(5L, TimeUnit.SECONDS)

        mediator.close()

        verify(consumer, times(PROCESSOR_RETRIES + 1)).poll(any())
    }

    @Test
    fun `mediator recreates components and continues after intermittent exception`() {
        // Once for the initial creation, once for the recreation
        val latch = CountDownLatch(2)
        whenever(lifecycleCoordinator.updateStatus(LifecycleStatus.UP)).then { latch.countDown() }

        // Exhaust the internal retries so that the components are reset
        whenever(consumer.poll(any()))
            .thenThrow(CordaMessageAPIIntermittentException("Intermittent 1..."))
            .thenThrow(CordaMessageAPIIntermittentException("Intermittent 2..."))
            .thenThrow(CordaMessageAPIIntermittentException("Intermittent 3..."))

        mediator.start()

        latch.await(5L, TimeUnit.SECONDS)

        mediator.close()

        verify(mediatorConsumerFactory, times(2)).create<Any, Any>(any())
        verify(messagingClientFactory, times(2)).create(any())
        verify(messageRouterFactory, times(2)).create(any())
    }

    @Test
    fun `mediator does not retry after fatal exception`() {
        val latch = CountDownLatch(1)
        whenever(lifecycleCoordinator.close()).then { latch.countDown() }
        whenever(consumer.poll(any()))
            .thenThrow(CordaMessageAPIFatalException("FATAL"))

        mediator.start()

        latch.await(5L, TimeUnit.SECONDS)

        mediator.close()

        // Ensure our components haven't been recreated
        verify(mediatorConsumerFactory, times(1)).create<Any, Any>(any())
        verify(messagingClientFactory, times(1)).create(any())
        verify(messageRouterFactory, times(1)).create(any())

        verify(lifecycleCoordinator).updateStatus(
            eq(LifecycleStatus.ERROR),
            eq("Error: Multi-source event mediator TestConfig failed to process messages, Fatal error occurred.")
        )
        verify(lifecycleCoordinator).close()
    }

    @Test
    fun `mediator stops after interrupted exception`() {
        mediator.start()
        
        Thread.sleep(1000)

        thread(name = "Interruption") {
            mediator.close()
        }.join()

        verify(mediatorConsumerFactory, times(1)).create<Any, Any>(any())
        verify(messagingClientFactory, times(1)).create(any())
        verify(messageRouterFactory, times(1)).create(any())

        verify(lifecycleCoordinator).close()
    }

    @Test
    fun `mediator retries after intermittent exceptions`() {
        val event1 = cordaConsumerRecords(KEY1, "event1")
        val sendCount = AtomicInteger(0)
        val errorsCount = PROCESSOR_RETRIES + 1
        val expectedProcessingCount = errorsCount + 1
        whenever(consumer.poll(any())).thenAnswer {
            if (sendCount.get() < expectedProcessingCount) {
                listOf(event1)
            } else {
                Thread.sleep(10)
                emptyList()
            }
        }

        whenever(messagingClient.send(any())).thenAnswer {
            if (sendCount.getAndIncrement() < errorsCount) {
                throw CordaMessageAPIIntermittentException("IntermittentException")
            }
            null
        }

        mediator.start()
        waitWhile(Duration.ofSeconds(TEST_TIMEOUT_SECONDS)) { sendCount.get() < expectedProcessingCount }
        mediator.close()

        verify(mediatorConsumerFactory, times(2)).create(any<MediatorConsumerConfig<Any, Any>>())
        verify(messagingClientFactory, times(2)).create(any<MessagingClientConfig>())
        verify(messageRouterFactory, times(2)).create(any<MessagingClientFinder>())
        verify(messageProcessor, times(expectedProcessingCount)).onNext(anyOrNull(), any())
        verify(consumer, atLeast(expectedProcessingCount)).poll(any())
        verify(messagingClient, times(expectedProcessingCount)).send(any())
    }

    @AfterEach
    fun tearDown() {
        // Close the mediator if a task fails early
        mediator.close()
    }

    private fun cordaConsumerRecords(key: String, event: String): CordaConsumerRecord<Any, Any> = CordaConsumerRecord(
        topic = "", partition = 0, offset = 0, key, event, timestamp = 0
    )
}
