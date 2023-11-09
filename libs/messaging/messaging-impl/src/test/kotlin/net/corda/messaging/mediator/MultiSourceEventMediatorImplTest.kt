package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messagebus.api.consumer.CordaConsumerRecord
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
import org.assertj.core.api.Assertions
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
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MultiSourceEventMediatorImplTest {
    companion object {
        private const val TEST_TIMEOUT_SECONDS = 20L
        private const val TEST_PROCESSOR_RETRIES = 3
        const val KEY1 = "key1"
        const val KEY2 = "key2"
    }

    private val messagingConfig = mock<SmartConfig>()
    private lateinit var config: EventMediatorConfig<Any, Any, Any>
    private lateinit var mediator: MultiSourceEventMediatorImpl<Any, Any, Any>
    private val mediatorConsumerFactory = mock<MediatorConsumerFactory>()
    private val consumer = mock<MediatorConsumer<Any, Any>>()
    private val messagingClientFactory = mock<MessagingClientFactory>()
    private val messagingClient = mock<MessagingClient>()
    private val messageProcessor = mock<StateAndEventProcessor<Any, Any, Any>>()
    private val messageRouterFactory = mock<MessageRouterFactory>()
    private val stateSerializer = mock<CordaAvroSerializer<Any>>()
    private val stateDeserializer = mock<CordaAvroDeserializer<Any>>()
    private val stateManager = mock<StateManager>()
    private val taskManager = mock<TaskManager>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()

    @BeforeEach
    fun beforeEach() {
        whenever(mediatorConsumerFactory.create(any<MediatorConsumerConfig<Any, Any>>())).thenReturn(consumer)

        whenever(messagingClient.send(any())).thenAnswer {
            null
        }

        whenever(messagingClientFactory.create(any<MessagingClientConfig>())).thenReturn(messagingClient)

        whenever(messageProcessor.keyClass).thenReturn(Any::class.java)
        whenever(messageProcessor.eventValueClass).thenReturn(Any::class.java)
        whenever(
            messageProcessor.onNext(
                anyOrNull(),
                any()
            )
        ).thenAnswer {
            StateAndEventProcessor.Response<Any>(
                updatedState = mock(),
                responseEvents = listOf(
                    Record(
                        topic = "", "key", value = mock(), timestamp = 0
                    )
                ),
            )
        }

        val messageRouter = MessageRouter { _ ->
            RoutingDestination.routeTo(messagingClient, "endpoint", RoutingDestination.Type.ASYNCHRONOUS)
        }
        whenever(messageRouterFactory.create(any<MessagingClientFinder>())).thenReturn(messageRouter)

        whenever(stateSerializer.serialize(any())).thenAnswer { ByteArray(0) }

        whenever(taskManager.executeLongRunningTask(any<() -> Any>())).thenAnswer { invocation ->
            val command = invocation.getArgument<() -> Any>(0)
            CompletableFuture.supplyAsync(command)
        }

        whenever(taskManager.executeShortRunningTask(any<() -> Any>())).thenAnswer { invocation ->
            val command = invocation.getArgument<() -> Any>(0)
            CompletableFuture.supplyAsync(command)
        }

        whenever(lifecycleCoordinatorFactory.createCoordinator(any(), anyOrNull())).thenReturn(mock())

        whenever(messagingConfig.getInt(eq(MessagingConfig.Subscription.PROCESSOR_RETRIES))).thenReturn(TEST_PROCESSOR_RETRIES)

        config = EventMediatorConfigBuilder<Any, Any, Any>()
            .name("Test")
            .messagingConfig(messagingConfig)
            .consumerFactories(mediatorConsumerFactory)
            .clientFactories(messagingClientFactory)
            .messageProcessor(messageProcessor)
            .messageRouterFactory(messageRouterFactory)
            .threads(1)
            .threadName("mediator-thread")
            .stateManager(stateManager)
            .build()

        mediator = MultiSourceEventMediatorImpl(
            config,
            stateSerializer,
            stateDeserializer,
            stateManager,
            taskManager,
            lifecycleCoordinatorFactory,
        )
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
        val batchNumber = AtomicInteger(0)
        whenever(consumer.poll(any())).thenAnswer {
            if (batchNumber.get() < eventBatches.size) {
                eventBatches[batchNumber.getAndIncrement()]
            } else {
                Thread.sleep(10)
                emptyList()
            }
        }

        mediator.start()
        waitWhile(Duration.ofSeconds(TEST_TIMEOUT_SECONDS)) { batchNumber.get() < eventBatches.size }
        mediator.close()

        verify(mediatorConsumerFactory).create(any<MediatorConsumerConfig<Any, Any>>())
        verify(messagingClientFactory).create(any<MessagingClientConfig>())
        verify(messageRouterFactory).create(any<MessagingClientFinder>())
        verify(messageProcessor, times(events.size)).onNext(anyOrNull(), any())
        verify(stateManager, times(eventBatches.size)).get(any())
        verify(stateManager, times(eventBatches.size)).create(any())
        verify(consumer, atLeast(eventBatches.size)).poll(any())
        verify(consumer, times(eventBatches.size)).syncCommitOffsets()
        verify(messagingClient, times(events.size)).send(any())
    }

//    @Test
    fun `mediator retries after intermittent exceptions`() {
        val event1 = cordaConsumerRecords(KEY1, "event1")
        val sendCount = AtomicInteger(0)
        val errorsCount = TEST_PROCESSOR_RETRIES + 1
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

    @Test
    fun qualifyStateMarksStateAsNewWhenOriginalIsNullAndProcessedIsNot() {
        val toCreate = ConcurrentHashMap<String, State?>()
        val toUpdate = ConcurrentHashMap<String, State?>()
        val toDelete = ConcurrentHashMap<String, State?>()
        val originalState = null
        val processedState = State("key1", "".toByteArray())
        mediator.qualifyState("groupKey", originalState, processedState, toCreate, toUpdate, toDelete)

        Assertions.assertThat(toCreate).containsExactly(Assertions.entry("groupKey", processedState))
        Assertions.assertThat(toUpdate).isEmpty()
        Assertions.assertThat(toDelete).isEmpty()
    }

    @Test
    fun qualifyStateMarksStateAsUpdatableWhenOriginalAndProcessedAreNotNull() {
        val toCreate = ConcurrentHashMap<String, State?>()
        val toUpdate = ConcurrentHashMap<String, State?>()
        val toDelete = ConcurrentHashMap<String, State?>()
        val originalState = State("key1", "".toByteArray())
        val processedState = State("key1", "nonEmpty".toByteArray())
        mediator.qualifyState("groupKey", originalState, processedState, toCreate, toUpdate, toDelete)

        Assertions.assertThat(toCreate).isEmpty()
        Assertions.assertThat(toUpdate).containsExactly(Assertions.entry("groupKey", processedState))
        Assertions.assertThat(toDelete).isEmpty()
    }

    @Test
    fun qualifyStateMarksStateAsDeletableWhenProcessedIsNullAndOriginalIsNot() {
        val toCreate = ConcurrentHashMap<String, State?>()
        val toUpdate = ConcurrentHashMap<String, State?>()
        val toDelete = ConcurrentHashMap<String, State?>()
        val originalState = State("key1", "".toByteArray())
        val processedState = null
        mediator.qualifyState("groupKey", originalState, processedState, toCreate, toUpdate, toDelete)

        Assertions.assertThat(toCreate).isEmpty()
        Assertions.assertThat(toUpdate).isEmpty()
        Assertions.assertThat(toDelete).containsExactly(Assertions.entry("groupKey", originalState))
    }

    private fun cordaConsumerRecords(key: String, event: String) =
        CordaConsumerRecord(
            topic = "", partition = 0, offset = 0, key, event, timestamp = 0
        )
}