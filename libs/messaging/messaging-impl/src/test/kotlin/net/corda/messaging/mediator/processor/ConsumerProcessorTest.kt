package net.corda.messaging.mediator.processor

import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.statemanager.api.StateManager
import net.corda.messagebus.api.consumer.CordaConsumerRecord
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
import net.corda.messaging.api.records.Record
import net.corda.messaging.getStringRecords
import net.corda.messaging.mediator.ConsumerProcessorState
import net.corda.messaging.mediator.GroupAllocator
import net.corda.messaging.mediator.MediatorState
import net.corda.taskmanager.TaskManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

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
    private lateinit var mediatorState: MediatorState
    private lateinit var consumerProcessorState: ConsumerProcessorState
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
        mediatorState = MediatorState()
        consumerProcessorState = ConsumerProcessorState()
        eventProcessor = mock()
        eventMediatorConfig = buildStringTestConfig()
        consumerProcessor = ConsumerProcessor(
            eventMediatorConfig, groupAllocator, taskManager, messageRouter, mediatorState,
            consumerProcessorState, eventProcessor
        )
    }


    @Test
    fun `poll returns messages divided into 2 groups, both groups are processed, each group produces 1 async output which is sent`() {
        whenever(taskManager.executeShortRunningTask<Unit>(any())).thenAnswer {
            consumerProcessorState.asynchronousOutputs.compute("key") { _, value ->
                value?.plus(getAsyncMediatorMessage("payload"))?.toMutableList() ?: mutableListOf(getAsyncMediatorMessage("payload"))
            }
            val future = CompletableFuture<Unit>()
            future.complete(Unit)
            future
        }
        whenever(messageRouter.getDestination(any())).thenReturn(RoutingDestination(client, "endpoint",
            RoutingDestination.Type.ASYNCHRONOUS))
        whenever(groupAllocator.allocateGroups<String, String, String>(any(), any())).thenReturn(getGroups(2, 4))

        consumerProcessor.processTopic(getConsumerFactory(), getConsumerConfig())

        verify(consumer, times(1)).poll(any())
        verify(consumerFactory, times(1)).create<String, String>(any())
        verify(consumer, times(1)).subscribe()
        verify(groupAllocator, times(1)).allocateGroups<String, String, String>(any(), any())
        verify(taskManager, times(2)).executeShortRunningTask<Unit>(any())

        verify(stateManager, times(2)).get(any())
        verify(stateManager, times(1)).create(any())
        verify(stateManager, times(1)).update(any())
        verify(stateManager, times(1)).delete(any())
        verify(consumer, times(1)).syncCommitOffsets()

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
        verify(taskManager, times(0)).executeShortRunningTask<Unit>(any())

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
        assertThrows<CordaMessageAPIFatalException> {  consumerProcessor.processTopic(consumerFactory, getConsumerConfig()) }

        verify(consumer, times(0)).poll(any())
        verify(consumerFactory, times(1)).create<String, String>(any())
        verify(consumer, times(1)).subscribe()
        verify(groupAllocator, times(0)).allocateGroups<String, String, String>(any(), any())
        verify(taskManager, times(0)).executeShortRunningTask<Unit>(any())

        verify(consumer, times(0)).resetEventOffsetPosition()
        verify(consumer, times(1)).close()
    }



    private fun getGroups(groupCount: Int, recordCountPerGroup: Int): List<Map<String, List<Record<String, String>>>> {
        val groups = mutableListOf<Map<String, List<Record<String, String>>>>()
        for (i in 0 until groupCount) {
            val key = "key$i"
            val records = getStringRecords(recordCountPerGroup, key)
            val map = mapOf(key to records)
            groups.add(map)
        }

        return groups
    }

    private fun getConsumerFactory(): MediatorConsumerFactory {
        consumer.apply {
            whenever(poll(any())).thenAnswer {
                mediatorState.stop()
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
        SmartConfigImpl.empty(),
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
