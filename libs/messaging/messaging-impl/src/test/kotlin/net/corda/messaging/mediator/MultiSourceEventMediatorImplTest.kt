package net.corda.messaging.mediator

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MultiSourceEventMediator
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
import net.corda.taskmanager.TaskManager
import net.corda.test.util.waitWhile
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MultiSourceEventMediatorImplTest {
    companion object {
        private const val TEST_TIMEOUT_SECONDS = 20L
        const val KEY1 = "key1"
        const val KEY2 = "key2"
    }

    private lateinit var config: EventMediatorConfig<Any, Any, Any>
    private lateinit var mediator: MultiSourceEventMediator<Any, Any, Any>
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
    private val lifecycleCoordinator = mock<LifecycleCoordinator>()

    @BeforeEach
    fun beforeEach() {
        whenever(mediatorConsumerFactory.create(any<MediatorConsumerConfig<Any, Any>>())).thenReturn(consumer)

        whenever(messagingClient.send(any())).thenAnswer {
            null
        }

        whenever(messagingClientFactory.create(any<MessagingClientConfig>())).thenReturn(messagingClient)

        whenever(messageProcessor.keyClass).thenReturn(Any::class.java)
        whenever(messageProcessor.eventValueClass).thenReturn(Any::class.java)


        val messageRouter = MessageRouter { _ ->
            RoutingDestination.routeTo(messagingClient, "endpoint")
        }
        whenever(messageRouterFactory.create(any<MessagingClientFinder>())).thenReturn(messageRouter)

        whenever(stateSerializer.serialize(any())).thenAnswer { ByteArray(0) }

        whenever(taskManager.executeLongRunningTask (any<() -> Any>())).thenAnswer { invocation ->
            val command = invocation.getArgument<() -> Any>(1)
            CompletableFuture.supplyAsync(command)
        }

        whenever(lifecycleCoordinatorFactory.createCoordinator(any(), anyOrNull())).thenReturn(lifecycleCoordinator)

        config = EventMediatorConfigBuilder<Any, Any, Any>().name("Test").messagingConfig(mock())
            .consumerFactories(mediatorConsumerFactory).clientFactories(messagingClientFactory)
            .messageProcessor(messageProcessor).messageRouterFactory(messageRouterFactory).build()

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

    @Test
    fun `mediator retries after intermittent exception`() {
        //                  test setup
        val eventsToBeProcessed = 5
        val events = (1..5).map {
            cordaConsumerRecords(it.toString(), "test + $it")
        }
        whenever(consumer.poll(any())).thenReturn(events)

        var exceptionThrown = false
        whenever(consumer.subscribe()).doAnswer {
            if (!exceptionThrown) {
                exceptionThrown = true
                throw CordaMessageAPIIntermittentException("test")
            }
        }

        //set up message processor that tells us when we've processed the right number of events (the signal from one thread to the test thread)
        //make a msg processor that takes a latch
        val latch = CountDownLatch(eventsToBeProcessed)
        whenever(messageProcessor.onNext(any(), any())).then {
            latch.countDown()
        }.thenAnswer {
            StateAndEventProcessor.Response<Any>(
                updatedState = mock(),
                responseEvents = listOf(
                    Record(
                        topic = "", "key", value = mock(), timestamp = 0
                    )
                ),
            )
        }

        //              test execution
        //start mediator
        mediator.start()
        //wait for signal - true for success, false for timeout
        assertTrue(latch.await(10, TimeUnit.SECONDS))

        //              test verification
        //check consumers list to check each item is closed
        //check clients list to check each item is closed
        verify(consumer).close()
        verify(messagingClient).close()
        //1) all events processed - e.g. push 5, process 5
        verify(messageProcessor, times(5)).onNext(any(), any())

        //2) verify mocks recreated by apis being called again
        //consumer mock, client mock, message router mock:

        verify(mediatorConsumerFactory, times(2)).create<Any, Any>(any())
        verify(messagingClientFactory, times(2)).create(any())
        verify(messageRouterFactory, times(2)).create(any())

        //3) verify api that sets it to up is called
        verify(lifecycleCoordinator).updateStatus(LifecycleStatus.UP)
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