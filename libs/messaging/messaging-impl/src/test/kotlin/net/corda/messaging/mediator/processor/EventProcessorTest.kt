package net.corda.messaging.mediator.processor

import net.corda.data.messaging.mediator.MediatorState
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.RoutingDestination
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor.Response
import net.corda.messaging.api.records.Record
import net.corda.messaging.getStringRecords
import net.corda.messaging.mediator.StateManagerHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertContains
import kotlin.test.assertNotNull

@Execution(ExecutionMode.SAME_THREAD)
class EventProcessorTest {
    private lateinit var eventMediatorConfig: EventMediatorConfig<String, String, String>
    private lateinit var stateManagerHelper: StateManagerHelper<String>
    private lateinit var client: MessagingClient
    private lateinit var messageRouter: MessageRouter
    private lateinit var mediatorReplayService: MediatorReplayService
    private lateinit var stateAndEventProcessor: StateAndEventProcessor<String, String, String>
    private lateinit var eventProcessor: EventProcessor<String, String, String>

    private val state1: State = mock()
    private val state2: State = mock()
    private val asyncMessage: String = "ASYNC_PAYLOAD"
    private val syncMessage: String = "SYNC_PAYLOAD"

    @BeforeEach
    @Suppress("unchecked_cast")
    fun setup() {
        client = mock()
        stateAndEventProcessor = mock()
        stateManagerHelper = mock()
        mediatorReplayService = mock<MediatorReplayService>().apply {
            whenever(getReplayEvents<String, String>(anyOrNull(), anyOrNull())).thenReturn(emptyMap())
        }
        messageRouter = mock()
        whenever(messageRouter.getDestination(any())).thenAnswer {
            val msg = it.arguments[0] as MediatorMessage<String>
            if (msg.payload == syncMessage) {
                RoutingDestination(client, "endpoint", RoutingDestination.Type.SYNCHRONOUS)
            } else RoutingDestination(client, "endpoint", RoutingDestination.Type.ASYNCHRONOUS)
        }
        eventMediatorConfig = buildTestConfig(true)

        whenever(stateAndEventProcessor.onNext(anyOrNull(), any())).thenAnswer {
            Response(
                StateAndEventProcessor.State("bar", null), listOf(
                    Record("", "key", asyncMessage),
                    Record("", "key", syncMessage)
                )
            )
        }

        eventProcessor = EventProcessor(eventMediatorConfig, stateManagerHelper, messageRouter, mediatorReplayService)
    }

    @Test
    fun `processed record triggers 2 successive synchronous calls which are processed immediately, each input produces 1 async output`() {
        var counter = 0
        whenever(stateAndEventProcessor.onNext(anyOrNull(), any())).thenAnswer {
            if (counter == 3) {
                Response<String>(null, emptyList())
            } else {
                counter++
                Response(
                    null, listOf(
                        Record("", "key", asyncMessage),
                        Record("", "key", syncMessage)
                    )
                )
            }
        }
        whenever(client.send(any())).thenReturn(MediatorMessage(syncMessage))
        val input = mapOf("key" to EventProcessingInput("key", getStringRecords(1, "key"), state1))
        eventProcessor.processEvents(input)

        verify(stateManagerHelper, times(1)).deserializeValue(any())
        verify(stateAndEventProcessor, times(4)).onNext(anyOrNull(), any())
        verify(messageRouter, times(9)).getDestination(any())
        verify(client, times(3)).send(any())
        verify(mediatorReplayService, times(1)).getOutputEvents<String, String>(any(), any())
        verify(mediatorReplayService, times(1)).getReplayEvents<String, String>(any(), any())
        verify(stateManagerHelper, times(1)).createOrUpdateState(any(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `When save replays is set to false, replays are not saved`() {
        eventMediatorConfig = buildTestConfig(false)
        eventProcessor = EventProcessor(eventMediatorConfig, stateManagerHelper, messageRouter, mediatorReplayService)

        whenever(stateAndEventProcessor.onNext(anyOrNull(), any())).thenReturn(Response(
                    null, listOf(
                        Record("", "key", asyncMessage)
                    )
                ))

        val input = mapOf("key" to EventProcessingInput("key", getStringRecords(1, "key"), state1))
        eventProcessor.processEvents(input)

        verify(stateManagerHelper, times(1)).deserializeValue(any())
        verify(stateAndEventProcessor, times(1)).onNext(anyOrNull(), any())
        verify(messageRouter, times(1)).getDestination(any())
        verify(client, times(0)).send(any())
        verify(mediatorReplayService, times(0)).getOutputEvents<String, String>(any(), any())
        verify(mediatorReplayService, times(0)).getReplayEvents<String, String>(any(), any())
        verify(stateManagerHelper, times(1)).createOrUpdateState(any(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `when the rpc client fails to send a message, a state is output with the correct metadata key filled in`() {

        whenever(client.send(any())).thenThrow(CordaMessageAPIIntermittentException("baz"))
        whenever(stateManagerHelper.failStateProcessing(any(), anyOrNull(), any())).thenReturn(mock())

        val input = mapOf("key" to EventProcessingInput("key", getStringRecords(1, "key"), state1))
        val outputMap = eventProcessor.processEvents(input)

        val output = outputMap["key"]
        assertEquals(emptyList<MediatorMessage<Any>>(), output?.asyncOutputs)
        verify(stateManagerHelper).failStateProcessing(any(), anyOrNull(), any())
    }

    @Test
    fun `When all inputs are replays, processor is not executed and replayed outputs are returned`() {
        val expectedOutputPerInput = MediatorMessage<Any>("payload")
        val recordCount = 2
        val key1Records = getStringRecords(recordCount, "key1")
        val key2Records = getStringRecords(recordCount, "key2")
        whenever(mediatorReplayService.getReplayEvents<String, String>(anyOrNull(), anyOrNull())).thenReturn(
            (key1Records + key2Records).associateWith {
                listOf(expectedOutputPerInput)
            }
        )

        val input = mapOf(
            "key1" to EventProcessingInput("key1", getStringRecords(recordCount, "key1"), state1),
            "key2" to EventProcessingInput("key2", getStringRecords(recordCount, "key2"), state2),
        )

        val result = eventProcessor.processEvents(input)
        assertNotNull(result)
        assertEquals(2, result.size)
        result.values.forEach {
            assertEquals(StateChangeAndOperation.Noop, it.stateChangeAndOperation)
            assertEquals(4, it.asyncOutputs.size)
            assertContains(it.asyncOutputs, expectedOutputPerInput)
        }
        verify(stateManagerHelper, times(2)).deserializeValue(any())
        verify(stateAndEventProcessor, times(0)).onNext(anyOrNull(), any())
        verify(messageRouter, times(0)).getDestination(any())
        verify(client, times(0)).send(any())
        verify(mediatorReplayService, times(0)).getOutputEvents<String, String>(any(), any())
        verify(stateManagerHelper, times(0)).createOrUpdateState(any(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `When some keys are replays, processor is executed for non replays and replayed outputs are returned for replayed inputs`() {
        val expectedOutputPerInput = MediatorMessage<Any>("payload")
        val recordCount = 2
        val mediatorState2 = mock<MediatorState>()
        whenever(stateManagerHelper.deserializeMediatorState(state2)).thenReturn(mediatorState2)
        val key1Records = getStringRecords(recordCount, "key1")
        val key2Records = getStringRecords(recordCount, "key2")
        (key1Records + key2Records).forEach {
            whenever(mediatorReplayService.getReplayEvents<String, String>(anyOrNull(), eq(mediatorState2))).thenReturn(
                key2Records.associateWith {
                    listOf(expectedOutputPerInput)
                }
            )
        }

        val input = mapOf(
            "key1" to EventProcessingInput("key1", getStringRecords(recordCount, "key1"), state1),
            "key2" to EventProcessingInput("key2", getStringRecords(recordCount, "key2"), state2),
        )

        val result = eventProcessor.processEvents(input)
        assertNotNull(result)
        assertEquals(2, result.size)

        result["key1"].let {
            assertNotNull(it)
            assertEquals(StateChangeAndOperation.Delete::class.java, it.stateChangeAndOperation::class.java)
            assertEquals(recordCount, it.asyncOutputs.size)
            assertEquals(it.asyncOutputs.first().payload, asyncMessage)
        }
        result["key2"].let {
            assertNotNull(it)
            assertEquals(StateChangeAndOperation.Noop, it.stateChangeAndOperation)
            assertEquals(recordCount, it.asyncOutputs.size)
            assertContains(it.asyncOutputs, expectedOutputPerInput)
        }
        verify(stateManagerHelper, times(2)).deserializeValue(any())
        verify(stateAndEventProcessor, times(2)).onNext(anyOrNull(), any())
        verify(messageRouter, times(6)).getDestination(any())
        verify(client, times(2)).send(any())
        verify(mediatorReplayService, times(2)).getReplayEvents<String, String>(any(), any())
        verify(mediatorReplayService, times(1)).getOutputEvents<String, String>(any(), any())
        verify(stateManagerHelper, times(1)).createOrUpdateState(any(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `When some records in same key are replays, processor is executed for non replays and replayed outputs are returned`() {
        val expectedOutputPerInput = MediatorMessage<Any>("payload")
        val mediatorState1 = mock<MediatorState>()
        val recordCount = 2
        val key1Records = getStringRecords(recordCount, "key1")
        whenever(stateManagerHelper.deserializeMediatorState(state1)).thenReturn(mediatorState1)
        whenever(mediatorReplayService.getReplayEvents<String, String>(any(), eq(mediatorState1))).thenReturn(
            mapOf(key1Records.first() to listOf(expectedOutputPerInput))
        )

        val input = mapOf(
            "key1" to EventProcessingInput("key1", key1Records, state1)
        )

        val result = eventProcessor.processEvents(input)
        assertNotNull(result)
        assertEquals(1, result.size)

        result["key1"].let { eventProcessingOutput ->
            assertNotNull(eventProcessingOutput)
            assertEquals(StateChangeAndOperation.Delete::class.java, eventProcessingOutput.stateChangeAndOperation::class.java)
            assertEquals(2, eventProcessingOutput.asyncOutputs.size)
            assertContains(eventProcessingOutput.asyncOutputs, expectedOutputPerInput)
            assert(eventProcessingOutput.asyncOutputs.any { it.payload == asyncMessage })

        }
        verify(stateManagerHelper, times(1)).deserializeValue(any())
        verify(stateAndEventProcessor, times(1)).onNext(anyOrNull(), any())
        verify(messageRouter, times(3)).getDestination(any())
        verify(client, times(1)).send(any())
        verify(mediatorReplayService, times(1)).getReplayEvents<String, String>(any(), any())
        verify(mediatorReplayService, times(1)).getOutputEvents<String, String>(any(), any())
        verify(stateManagerHelper, times(1)).createOrUpdateState(any(), anyOrNull(), any(), anyOrNull())
    }

    private fun buildTestConfig(saveReplays: Boolean) = EventMediatorConfig(
        "",
        SmartConfigImpl.empty(),
        emptyList(),
        emptyList(),
        stateAndEventProcessor,
        mock<MessageRouterFactory>(),
        1,
        "",
        mock(),
        20,
        saveReplays
    )
}
