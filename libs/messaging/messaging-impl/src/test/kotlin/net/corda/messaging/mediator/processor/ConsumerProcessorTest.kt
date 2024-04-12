package net.corda.messaging.mediator.processor

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.constants.MessagingMetadataKeys.PROCESSING_FAILURE
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.RoutingDestination
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.getStringRecords
import net.corda.messaging.mediator.GroupAllocator
import net.corda.messaging.mediator.MediatorSubscriptionState
import net.corda.messaging.mediator.StateManagerHelper
import net.corda.schema.configuration.MessagingConfig
import net.corda.taskmanager.TaskManager
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Future
import java.util.concurrent.TimeoutException

@Execution(ExecutionMode.SAME_THREAD)
class ConsumerProcessorTest {

    private lateinit var consumerProcessor: ConsumerProcessor<String, String, String>

    private lateinit var eventMediatorConfig: EventMediatorConfig<String, String, String>

    private lateinit var client: MessagingClient
    private lateinit var stateManager: StateManager
    private lateinit var consumer: MediatorConsumer<String, String>
    private lateinit var consumerFactory: MediatorConsumerFactory
    private lateinit var groupAllocator: GroupAllocator
    private lateinit var taskManager: TaskManager
    private lateinit var messageRouter: MessageRouter
    private lateinit var mediatorSubscriptionState: MediatorSubscriptionState
    private lateinit var stateManagerHelper: StateManagerHelper<String>
    private lateinit var eventProcessor: EventProcessor<String, String, String>


    @BeforeEach
    fun setup() {
        client = mock()
        taskManager = mock()
        stateManager = mock()
        consumer = mock()
        consumerFactory = mock()
        groupAllocator = mock()
        messageRouter = mock()
        mediatorSubscriptionState = MediatorSubscriptionState()
        eventProcessor = mock()
        eventMediatorConfig = buildStringTestConfig()
        stateManagerHelper = mock()
        consumerProcessor = ConsumerProcessor(
            eventMediatorConfig, groupAllocator, taskManager, messageRouter, mediatorSubscriptionState, eventProcessor, stateManagerHelper
        )
    }


    @Test
    fun `poll returns messages divided into 2 groups, both groups are processed, each group produces 1 async output which is sent`() {
        var counter = 0
        whenever(taskManager.executeShortRunningTask<Unit>(any(), any(), any())).thenAnswer {
            counter++
            val output = mapOf<String, EventProcessingOutput<String, Long>>(
                "foo-$counter" to EventProcessingOutput(
                    listOf(getAsyncMediatorMessage("payload")),
                    StateChangeAndOperation.Noop(),
                    listOf()
                )
            )
            val future = CompletableFuture<Map<String, EventProcessingOutput<String, Long>>>()
            future.complete(output)
            future
        }
        whenever(messageRouter.getDestination(any())).thenReturn(
            RoutingDestination(
                client, "endpoint",
                RoutingDestination.Type.ASYNCHRONOUS
            )
        )
        whenever(groupAllocator.allocateGroups<String, String, String>(any(), any())).thenReturn(
            getGroups(2, 4)
        )
        whenever(stateManagerHelper.createOrUpdateState(any(), any(), any())).thenReturn(mock())
        whenever(stateManager.get(any())).thenReturn(mapOf())

        consumerProcessor.processTopic(getConsumerFactory(), getConsumerConfig())

        verify(consumer, times(1)).poll(any())
        verify(consumerFactory, times(1)).create<String, String>(any())
        verify(consumer, times(1)).subscribe()
        verify(groupAllocator, times(1)).allocateGroups<String, String, String>(any(), any())
        verify(taskManager, times(2)).executeShortRunningTask<Unit>(any(), any(), any())

        verify(stateManager, times(2)).get(any())
        verify(stateManager, times(1)).create(any())
        verify(stateManager, times(1)).update(any())
        verify(stateManager, times(1)).delete(any())
        verify(consumer, times(1)).syncCommitOffsets(listOf())

        verify(messageRouter, times(2)).getDestination(any())
        verify(client, times(2)).send(any())

        verify(consumer, times(1)).close()
    }


    @Test
    fun `completion exception with intermittent exception as the cause is treated as intermittent`() {
        whenever(consumer.subscribe()).doThrow(CompletionException(CordaMessageAPIIntermittentException("exception")))
        whenever(groupAllocator.allocateGroups<String, String, String>(any(), any())).thenReturn(emptyList())

        consumerProcessor.processTopic(getConsumerFactory(), getConsumerConfig())

        verify(consumer, times(1)).poll(any())
        verify(consumerFactory, times(1)).create<String, String>(any())
        verify(consumer, times(1)).subscribe()
        verify(groupAllocator, times(1)).allocateGroups<String, String, String>(any(), any())
        verify(taskManager, times(0)).executeShortRunningTask<Unit>(any(), any(), any())

        verify(consumer, times(1)).resetEventOffsetPosition()
        verify(consumer, times(1)).close()
    }

    @Test
    fun `Fatal exception closes the consumer and stops processing`() {
        whenever(consumer.subscribe()).doThrow(CordaMessageAPIFatalException("exception"))
        whenever(groupAllocator.allocateGroups<String, String, String>(any(), any())).thenReturn(emptyList())
        val consumerFactory = getConsumerFactory()
        consumer.apply {
            whenever(poll(any())).thenReturn(listOf(getConsumerRecord()))
        }
        assertThrows(CordaMessageAPIFatalException::class.java) {
            consumerProcessor.processTopic(consumerFactory, getConsumerConfig())
        }

        verify(consumer, times(0)).poll(any())
        verify(consumerFactory, times(1)).create<String, String>(any())
        verify(consumer, times(1)).subscribe()
        verify(groupAllocator, times(0)).allocateGroups<String, String, String>(any(), any())
        verify(taskManager, times(0)).executeShortRunningTask<Unit>(any(), any(), any())

        verify(consumer, times(0)).resetEventOffsetPosition()
        verify(consumer, times(1)).close()
    }

    @Test
    fun `Exception when committing to the bus results in no delete operations`() {
        var counter = 0
        whenever(taskManager.executeShortRunningTask<Unit>(any(), any(), any())).thenAnswer {
            counter++
            val output = mapOf(
                "foo-$counter" to EventProcessingOutput(
                    listOf(getAsyncMediatorMessage("payload")),
                    StateChangeAndOperation.Noop(),
                    listOf<CordaConsumerRecord<String, Long>>()
                )
            )
            val future = CompletableFuture<Map<String, EventProcessingOutput<String, Long>>>()
            future.complete(output)
            future
        }
        whenever(messageRouter.getDestination(any())).thenReturn(
            RoutingDestination(
                client, "endpoint",
                RoutingDestination.Type.ASYNCHRONOUS
            )
        )
        whenever(groupAllocator.allocateGroups<String, String, String>(any(), any())).thenReturn(
            getGroups(2, 4),
            getGroups(2, 4)
        )
        whenever(stateManagerHelper.createOrUpdateState(any(), any(), any())).thenReturn(mock())
        whenever(stateManager.get(any())).thenReturn(mapOf())
        doThrow(CordaRuntimeException("Oops")).doNothing().`when`(consumer).syncCommitOffsets(listOf())

        consumerProcessor.processTopic(getConsumerFactory(2), getConsumerConfig())

        verify(consumer, times(2)).poll(any())
        verify(consumerFactory, times(1)).create<String, String>(any())
        verify(consumer, times(1)).subscribe()
        verify(groupAllocator, times(2)).allocateGroups<String, String, String>(any(), any())
        verify(taskManager, times(4)).executeShortRunningTask<Unit>(any(), any(), any())

        verify(stateManager, times(4)).get(any())
        verify(stateManager, times(2)).create(any())
        verify(stateManager, times(2)).update(any())

        verify(messageRouter, times(4)).getDestination(any())
        verify(client, times(4)).send(any())

        verify(consumer, times(2)).syncCommitOffsets(listOf())
        verify(consumer, times(1)).close()
        verify(stateManager, times(1)).delete(any())
    }

    @Test
    fun `when event processing times out, mark all states in the group as failed`() {
        val future1 = mock<Future<Unit>>().apply {
            whenever(get(any(), any())).thenThrow(TimeoutException("Timed out"))
        }
        val future2 = mock<Future<Unit>>().apply {
            whenever(get(any(), any())).thenThrow(TimeoutException("Timed out"))
        }
        whenever(taskManager.executeShortRunningTask<Unit>(any(), any(), any()))
            .thenReturn(future1)
            .thenReturn(future2)
        whenever(stateManagerHelper.failStateProcessing(any(), anyOrNull(), any())).thenReturn(mock())
        whenever(groupAllocator.allocateGroups<String, String, String>(any(), any())).thenReturn(getGroups(2, 4), listOf())

        consumerProcessor.processTopic(getConsumerFactory(), getConsumerConfig())

        verify(stateManagerHelper, times(2)).failStateProcessing(any(), anyOrNull(), any())
        verify(future1, times(1)).cancel(true)
        verify(future2, times(1)).cancel(true)
    }

    @Test
    fun `when the state for a set of events is marked as failed, no further processing occurs`() {
        whenever(consumer.poll(any())).thenReturn(listOf(CordaConsumerRecord("a", 0, 0, "key", "b", 0L)))
        val metadata = Metadata(mapOf(PROCESSING_FAILURE to true))
        val captor = argumentCaptor<List<EventProcessingInput<String, String>>>()
        whenever(stateManager.get(any())).thenReturn(mapOf("key" to State("key", byteArrayOf(), metadata = metadata)))
        whenever(groupAllocator.allocateGroups<String, String, String>(captor.capture(), any())).thenAnswer {
            captor.allValues.mapNotNull {
                if (it.isNotEmpty()) {
                    mapOf("key" to it)
                } else {
                    null
                }
            }
        }
        consumerProcessor.processTopic(getConsumerFactory(), getConsumerConfig())

        verify(taskManager, never()).executeShortRunningTask<Unit>(any(), any(), any())
    }

    @Test
    fun `when a set of events fail with transient errors, an infinite number of retries occur`() {
        var counter = 0
        whenever(taskManager.executeShortRunningTask<Unit>(any(), any(), any())).thenAnswer {
            counter++
            val output = mapOf(
                "foo-$counter" to EventProcessingOutput(
                    listOf(getAsyncMediatorMessage("payload")),
                    StateChangeAndOperation.Transient,
                    listOf<CordaConsumerRecord<String, Int>>()
                )
            )
            val future = CompletableFuture<Map<String, EventProcessingOutput<String, Int>>>()
            future.complete(output)
            future
        }
        whenever(messageRouter.getDestination(any())).thenReturn(
            RoutingDestination(
                client, "endpoint",
                RoutingDestination.Type.ASYNCHRONOUS
            )
        )
        val metadata = Metadata(mapOf(PROCESSING_FAILURE to true))
        val mockState = mock<State>()
        whenever(mockState.metadata).thenReturn(metadata)
        whenever(stateManagerHelper.failStateProcessing(any(), anyOrNull(), any())).thenReturn(mockState)
        whenever(groupAllocator.allocateGroups<String, String, String>(any(), any())).thenReturn(
            getGroups(2, 4)
        )
        whenever(stateManagerHelper.createOrUpdateState(any(), any(), any())).thenReturn(mock())
        whenever(stateManager.get(any())).thenReturn(mapOf())

        consumerProcessor.processTopic(getConsumerFactory(6), getConsumerConfig())

        verify(consumer, times(6)).resetEventOffsetPosition()
        verify(consumer, never()).syncCommitOffsets(listOf())
    }

    @Test
    fun `when a set of events fail with non-transient errors, an finite number of retries occur`() {
        // A timeout of processing is non-transient, because the processor may be stuck indefinitely.
        whenever(taskManager.executeShortRunningTask<Unit>(any(), any(), any())).thenAnswer {
            val future = CompletableFuture<Map<String, EventProcessingOutput<String, Int>>>()
            future.completeExceptionally(TimeoutException())
            future
        }
        whenever(messageRouter.getDestination(any())).thenReturn(
            RoutingDestination(
                client, "endpoint",
                RoutingDestination.Type.ASYNCHRONOUS
            )
        )
        val metadata = Metadata(mapOf(PROCESSING_FAILURE to true))
        val mockState = mock<State>()
        whenever(mockState.metadata).thenReturn(metadata)
        whenever(stateManagerHelper.failStateProcessing(any(), anyOrNull(), any())).thenReturn(mockState)
        whenever(groupAllocator.allocateGroups<String, String, String>(any(), any())).thenReturn(
            getGroups(2, 4)
        )
        whenever(stateManagerHelper.createOrUpdateState(any(), any(), any())).thenReturn(mock())
        whenever(stateManager.get(any())).thenReturn(mapOf())

        consumerProcessor.processTopic(getConsumerFactory(6), getConsumerConfig())

        verify(consumer, times(5)).resetEventOffsetPosition()
        verify(consumer, times(1)).syncCommitOffsets(listOf())
    }

    private fun getGroups(groupCount: Int, recordCountPerGroup: Int): List<Map<String, EventProcessingInput<String, String>>> {
        val groups = mutableListOf<Map<String, EventProcessingInput<String, String>>>()
        for (i in 0 until groupCount) {
            val key = "key$i"
            val input = EventProcessingInput(key, getStringRecords(recordCountPerGroup, key), null)
            val map = mapOf(key to input)
            groups.add(map)
        }

        return groups
    }

    private fun getConsumerFactory(loops: Int = 1): MediatorConsumerFactory {
        consumer.apply {
            var attempts = 0
            whenever(poll(any())).thenAnswer {
                attempts++
                if (attempts >= loops) {
                    mediatorSubscriptionState.stop()
                }
                listOf(getConsumerRecord())
            }
        }
        consumerFactory.apply {
            whenever(create<String, String>(any())).thenReturn(consumer)
        }

        return consumerFactory
    }

    private fun getAsyncMediatorMessage(payload: Any) = MediatorMessage(payload, mutableMapOf())
    private fun getConsumerRecord() = CordaConsumerRecord("topic", 1, 1, "key", "value", Instant.now().toEpochMilli())
    private fun getConsumerConfig() = MediatorConsumerConfig(String::class.java, String::class.java) { }

    private fun buildStringTestConfig() = EventMediatorConfig(
        "",
        SmartConfigImpl.empty()
            .withValue(MessagingConfig.Subscription.MEDIATOR_PROCESSING_POLL_TIMEOUT, ConfigValueFactory.fromAnyRef(10))
            .withValue(MessagingConfig.Subscription.MEDIATOR_PROCESSING_PROCESSOR_TIMEOUT, ConfigValueFactory.fromAnyRef(1000)),
        emptyList(),
        emptyList(),
        mock<StateAndEventProcessor<String, String, String>>(),
        mock<MessageRouterFactory>(),
        1,
        "",
        stateManager,
        20
    )
}
