package net.corda.messaging.mediator.processor

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.constants.MessagingMetadataKeys.PROCESSING_FAILURE
import net.corda.messaging.api.exception.CordaMessageAPIConsumerResetException
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
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
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
        whenever(taskManager.executeShortRunningTask<Unit>(any())).thenAnswer {
            counter++
            val output = mapOf(
                "foo-$counter" to EventProcessingOutput(
                    listOf(getAsyncMediatorMessage("payload")),
                    StateChangeAndOperation.Noop
                )
            )
            val future = CompletableFuture<Map<String, EventProcessingOutput>>()
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
            listOf()
        )
        whenever(stateManagerHelper.createOrUpdateState(any(), any(), any())).thenReturn(mock())
        whenever(stateManager.get(any())).thenReturn(mapOf())

        consumerProcessor.processTopic(getConsumerFactory(), getConsumerConfig())

        verify(consumer, times(1)).poll(any())
        verify(consumerFactory, times(1)).create<String, String>(any())
        verify(consumer, times(1)).subscribe()
        verify(groupAllocator, times(2)).allocateGroups<String, String, String>(any(), any())
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
        assertThrows(CordaMessageAPIFatalException::class.java) {
            consumerProcessor.processTopic(consumerFactory, getConsumerConfig())
        }

        verify(consumer, times(0)).poll(any())
        verify(consumerFactory, times(1)).create<String, String>(any())
        verify(consumer, times(1)).subscribe()
        verify(groupAllocator, times(0)).allocateGroups<String, String, String>(any(), any())
        verify(taskManager, times(0)).executeShortRunningTask<Unit>(any())

        verify(consumer, times(0)).resetEventOffsetPosition()
        verify(consumer, times(1)).close()
    }

    @Test
    fun `Exception when committing to the bus results in no delete operations`() {
        var counter = 0
        whenever(taskManager.executeShortRunningTask<Unit>(any())).thenAnswer {
            counter++
            val output = mapOf(
                "foo-$counter" to EventProcessingOutput(
                    listOf(getAsyncMediatorMessage("payload")),
                    StateChangeAndOperation.Noop
                )
            )
            val future = CompletableFuture<Map<String, EventProcessingOutput>>()
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
            listOf()
        )
        whenever(stateManagerHelper.createOrUpdateState(any(), any(), any())).thenReturn(mock())
        whenever(stateManager.get(any())).thenReturn(mapOf())
        whenever(consumer.syncCommitOffsets()).doThrow(CordaRuntimeException("Oops"))

        assertThrows<CordaRuntimeException> { consumerProcessor.processTopic(getConsumerFactory(), getConsumerConfig()) }

        verify(consumer, times(1)).poll(any())
        verify(consumerFactory, times(1)).create<String, String>(any())
        verify(consumer, times(1)).subscribe()
        verify(groupAllocator, times(2)).allocateGroups<String, String, String>(any(), any())
        verify(taskManager, times(2)).executeShortRunningTask<Unit>(any())

        verify(stateManager, times(2)).get(any())
        verify(stateManager, times(1)).create(any())
        verify(stateManager, times(1)).update(any())

        verify(messageRouter, times(2)).getDestination(any())
        verify(client, times(2)).send(any())

        verify(consumer, times(1)).syncCommitOffsets()
        verify(consumer, times(1)).close()
        verify(stateManager, times(0)).delete(any())
    }

    @Test
    fun `when event processing times out, mark all states in the group as failed`() {
        whenever(taskManager.executeShortRunningTask<Unit>(any())).thenAnswer {
            val future = CompletableFuture<Map<String, EventProcessingOutput>>()
            future.completeExceptionally(TimeoutException())
            future
        }
        whenever(stateManagerHelper.failStateProcessing(any(), anyOrNull(), any())).thenReturn(mock())
        whenever(groupAllocator.allocateGroups<String, String, String>(any(), any())).thenReturn(getGroups(2, 4), listOf())

        consumerProcessor.processTopic(getConsumerFactory(), getConsumerConfig())

        verify(stateManagerHelper, times(2)).failStateProcessing(any(), anyOrNull(), any())
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

        verify(taskManager, never()).executeShortRunningTask<Unit>(any())
    }

    @Test
    fun `ConsumerResetException from event processor resets consumer, does not write states`() {

        whenever(stateManager.get(any())).thenReturn(mapOf("key" to State("key", byteArrayOf())))
        whenever(groupAllocator.allocateGroups<String, String, String>(any(), any())).thenReturn(
            getGroups(1, 1),
            listOf()
        )
        whenever(taskManager.executeShortRunningTask<Unit>(any())).thenAnswer {
            val future = CompletableFuture<Map<String, EventProcessingOutput>>()
            future.completeExceptionally(CordaMessageAPIConsumerResetException("Consumer to be reset"))
            future
        }

        consumerProcessor.processTopic(getConsumerFactory(), getConsumerConfig())

        verify(consumer, times(1)).poll(any())
        verify(consumerFactory, times(1)).create<String, String>(any())
        verify(consumer, times(1)).subscribe()
        verify(groupAllocator, times(1)).allocateGroups<String, String, String>(any(), any())
        verify(taskManager, times(1)).executeShortRunningTask<Unit>(any())

        verify(stateManager, times(1)).get(any())
        verify(stateManager, times(0)).create(any())
        verify(stateManager, times(0)).update(any())
        verify(stateManager, times(0)).delete(any())

        verify(messageRouter, times(0)).getDestination(any())
        verify(client, times(0)).send(any())

        verify(consumer, times(1)).resetEventOffsetPosition()
        verify(consumer, times(1)).close() // we only allow this test to run the loop once
    }

    @Test
    fun `ConsumerResetException triggers batch retry, sucessfully processes event on second attempt`() {
        val key = UUID.randomUUID().toString()
        whenever(stateManager.get(any())).thenReturn(
            mapOf(key to State(key, byteArrayOf())),
            mapOf(key to State(key, byteArrayOf())),
            mapOf(), // third time is getting states that failed to create
        )
        whenever(groupAllocator.allocateGroups<String, String, String>(any(), any()))
            .thenReturn(
                getGroups(1, 1),
                getGroups(1, 1),
                emptyList(), // group allocation at the end, there are no failed records
            )
        whenever(taskManager.executeShortRunningTask<Unit>(any()))
            // first loop, exception
            .thenAnswer {
                val future = CompletableFuture<Map<String, EventProcessingOutput>>()
                future.completeExceptionally(CordaMessageAPIConsumerResetException("Consumer to be reset"))
                future
            }
            // second loop, succeeds (simulates fixed transient error)
            .thenAnswer {
                val output = mapOf(
                    key to EventProcessingOutput(
                        listOf(),
                        StateChangeAndOperation.Create(mock())
                    )
                )
                val future = CompletableFuture<Map<String, EventProcessingOutput>>()
                future.complete(output)
                future
            }
        consumer.apply {
            whenever(poll(any()))
                // first poll does not stop mediator subscription
                .thenAnswer {
                    listOf(getConsumerRecord())
                }
                // second poll stops mediator subscription
                .thenAnswer {
                    // second time around, calls poll
                    mediatorSubscriptionState.stop()
                    listOf(getConsumerRecord())
                }
        }

        consumerFactory.apply {
            whenever(create<String, String>(any())).thenReturn(consumer)
        }

        consumerProcessor.processTopic(consumerFactory, getConsumerConfig())

        verify(consumer, times(2)).poll(any())
        verify(consumerFactory, times(1)).create<String, String>(any())
        verify(consumer, times(1)).subscribe()
        verify(groupAllocator, times(3)).allocateGroups<String, String, String>(any(), any())
        verify(taskManager, times(2)).executeShortRunningTask<Unit>(any())

        verify(stateManager, times(3)).get(any()) // get is called after create to find keys that failed to be created
        verify(stateManager, times(1)).create(argThat { size == 1 })
        verify(stateManager, times(1)).update(argThat { isEmpty() } )
        verify(stateManager, times(1)).delete(argThat { isEmpty() } )

        verify(messageRouter, times(0)).getDestination(any())
        verify(client, times(0)).send(any())

        verify(consumer, times(1)).resetEventOffsetPosition() // consumer is reset one time
        verify(consumer, times(1)).close() // closed at end, after mediator subscription closes
    }

    @Test
    fun `ConsumerResetException triggers batch retry, successfully processes async mediator messages on second attempt`() {
        val key = UUID.randomUUID().toString()
        whenever(stateManager.get(any())).thenReturn(
            mapOf(key to State(key, byteArrayOf())),
            mapOf(key to State(key, byteArrayOf())),
            mapOf(), // third time is getting states that failed to create
        )
        whenever(groupAllocator.allocateGroups<String, String, String>(any(), any()))
            .thenReturn(
                getGroups(1, 1),
                getGroups(1, 1),
                emptyList(), // group allocation at the end, there are no failed records
            )
        whenever(taskManager.executeShortRunningTask<Unit>(any()))
            // first loop, exception
            .thenAnswer {
                val future = CompletableFuture<Map<String, EventProcessingOutput>>()
                future.completeExceptionally(CordaMessageAPIConsumerResetException("Consumer to be reset"))
                future
            }
            // second loop, succeeds (simulates fixed transient error)
            .thenAnswer {
                val output = mapOf(
                    key to EventProcessingOutput(
                        listOf(getAsyncMediatorMessage("payload")),
                        StateChangeAndOperation.Create(mock())
                    )
                )
                val future = CompletableFuture<Map<String, EventProcessingOutput>>()
                future.complete(output)
                future
            }
        consumer.apply {
            whenever(poll(any()))
                // first poll does not stop mediator subscription
                .thenAnswer {
                    listOf(getConsumerRecord("key"))
                }
                // second poll stops mediator subscription
                .thenAnswer {
                    // second time around, calls poll
                    mediatorSubscriptionState.stop()
                    listOf(getConsumerRecord("key"))
                }
        }

        consumerFactory.apply {
            whenever(create<String, String>(any())).thenReturn(consumer)
        }

        whenever(messageRouter.getDestination(any())).thenReturn(
            RoutingDestination(
                client, "endpoint",
                RoutingDestination.Type.ASYNCHRONOUS
            )
        )

        consumerProcessor.processTopic(consumerFactory, getConsumerConfig())

        verify(consumer, times(2)).poll(any())
        verify(consumerFactory, times(1)).create<String, String>(any())
        verify(consumer, times(1)).subscribe()
        verify(groupAllocator, times(3)).allocateGroups<String, String, String>(any(), any())
        verify(taskManager, times(2)).executeShortRunningTask<Unit>(any())

        verify(stateManager, times(3)).get(any()) // get is called after create to find keys that failed to be created
        verify(stateManager, times(1)).create(argThat { size == 1 })
        verify(stateManager, times(1)).update(argThat { isEmpty() } )
        verify(stateManager, times(1)).delete(argThat { isEmpty() } )

        verify(messageRouter, times(1)).getDestination(any())
        verify(client, times(1)).send(any())

        verify(consumer, times(1)).resetEventOffsetPosition() // consumer is reset one time
        verify(consumer, times(1)).close() // closed at end, after mediator subscription closes
    }

    @Test
    fun `ConsumerResetException triggers batch retry with multiple events, processes all events in group after retrying`() {
        val key = UUID.randomUUID().toString()
        val key2 = UUID.randomUUID().toString()
        whenever(stateManager.get(any())).thenReturn(
            mapOf(key to State(key, byteArrayOf()), key2 to State(key2, byteArrayOf())),
            mapOf(key to State(key, byteArrayOf()), key2 to State(key2, byteArrayOf())),
            mapOf(), // third time is getting states that failed to create
        )
        whenever(groupAllocator.allocateGroups<String, String, String>(any(), any()))
            .thenReturn(
                getGroups(2, 1),
                getGroups(2, 1),
                emptyList(), // group allocation at the end, there are no failed records
            )
        whenever(taskManager.executeShortRunningTask<Unit>(any()))
            // first loop, event 1, exception
            .thenAnswer {
                val future = CompletableFuture<Map<String, EventProcessingOutput>>()
                future.completeExceptionally(CordaMessageAPIConsumerResetException("Consumer to be reset"))
                future
            }
            // first loop, event 2, successful
            .thenAnswer {
                val output = mapOf(
                    key2 to EventProcessingOutput(
                        listOf(),
                        StateChangeAndOperation.Update(mock())
                    )
                )
                val future = CompletableFuture<Map<String, EventProcessingOutput>>()
                future.complete(output)
                future
            }
            // second loop, event 1, succeeds (simulates fixed transient error)
            .thenAnswer {
                val output = mapOf(
                    key to EventProcessingOutput(
                        listOf(),
                        StateChangeAndOperation.Create(mock())
                    )
                )
                val future = CompletableFuture<Map<String, EventProcessingOutput>>()
                future.complete(output)
                future
            }
            // second loop, event 2, success
            .thenAnswer {
                val output = mapOf(
                    key2 to EventProcessingOutput(
                        listOf(),
                        StateChangeAndOperation.Update(mock())
                    )
                )
                val future = CompletableFuture<Map<String, EventProcessingOutput>>()
                future.complete(output)
                future
            }

        consumer.apply {
            whenever(poll(any()))
                // first poll does not stop mediator subscription
                .thenAnswer {
                    listOf(getConsumerRecord(key), getConsumerRecord(key2))
                }
                // second poll stops mediator subscription
                .thenAnswer {
                    // second time around, calls poll
                    mediatorSubscriptionState.stop()
                    listOf(getConsumerRecord(key), getConsumerRecord(key2))
                }
        }

        consumerFactory.apply {
            whenever(create<String, String>(any())).thenReturn(consumer)
        }

        consumerProcessor.processTopic(consumerFactory, getConsumerConfig())

        verify(consumer, times(2)).poll(any())
        verify(consumerFactory, times(1)).create<String, String>(any())
        verify(consumer, times(1)).subscribe()
        verify(groupAllocator, times(3)).allocateGroups<String, String, String>(any(), any())
        verify(taskManager, times(4)).executeShortRunningTask<Unit>(any())

        verify(stateManager, times(3)).get(any()) // get is called after create to find keys that failed to be created
        verify(stateManager, times(1)).create(argThat { size == 1 }) // only called after group success
        verify(stateManager, times(1)).update(argThat { size == 1 } ) // only called after group success
        verify(stateManager, times(1)).delete(argThat { isEmpty() } )

        verify(messageRouter, times(0)).getDestination(any())
        verify(client, times(0)).send(any())

        verify(consumer, times(1)).resetEventOffsetPosition() // consumer is reset one time
        verify(consumer, times(1)).close() // closed at end, after mediator subscription closes
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

    private fun getConsumerFactory(): MediatorConsumerFactory {
        consumer.apply {
            whenever(poll(any())).thenAnswer {
                mediatorSubscriptionState.stop()
                listOf(getConsumerRecord())
            }
        }
        consumerFactory.apply {
            whenever(create<String, String>(any())).thenReturn(consumer)
        }

        return consumerFactory
    }

    private fun getAsyncMediatorMessage(payload: Any) = MediatorMessage(payload, mutableMapOf())
    private fun getConsumerRecord(key: String = "key") = CordaConsumerRecord("topic", 1, 1, key, "value", Instant.now().toEpochMilli())
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
