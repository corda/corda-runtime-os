package net.corda.messaging.mediator.processor

import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.mediator.MediatorInputService
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
import org.assertj.core.api.Assertions.assertThat
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
import java.util.UUID

@Execution(ExecutionMode.SAME_THREAD)
class EventProcessorTest {
    private lateinit var eventMediatorConfig: EventMediatorConfig<String, String, String>
    private lateinit var stateManagerHelper: StateManagerHelper<String>
    private lateinit var client: MessagingClient
    private lateinit var messageRouter: MessageRouter
    private lateinit var mediatorInputService: MediatorInputService
    private lateinit var stateAndEventProcessor: StateAndEventProcessor<String, String, String>
    private lateinit var eventProcessor: EventProcessor<String, String, String>

    private val inputState1: State = mock()
    private val asyncMessage: String = "ASYNC_PAYLOAD"
    private val syncMessage: String = "SYNC_PAYLOAD"
    private val updatedProcessingState = StateAndEventProcessor.State("bar", null)

    @BeforeEach
    @Suppress("unchecked_cast")
    fun setup() {
        client = mock()
        stateAndEventProcessor = mock()
        stateManagerHelper = mock()
        mediatorInputService = mock<MediatorInputService>().apply {
            whenever(getHash<String, String>(anyOrNull())).thenAnswer {
                UUID.randomUUID().toString()
            }
        }
        messageRouter = mock()
        whenever(messageRouter.getDestination(any())).thenAnswer {
            val msg = it.arguments[0] as MediatorMessage<String>
            if (msg.payload == syncMessage) {
                RoutingDestination(client, "endpoint", RoutingDestination.Type.SYNCHRONOUS)
            } else RoutingDestination(client, "endpoint", RoutingDestination.Type.ASYNCHRONOUS)
        }
        eventMediatorConfig = buildTestConfig()

        whenever(stateAndEventProcessor.onNext(anyOrNull(), any())).thenAnswer {
            Response(
                updatedProcessingState,
                listOf(
                    Record("", "key", asyncMessage),
                    Record("", "key", syncMessage)
                )
            )
        }

        eventProcessor = EventProcessor(eventMediatorConfig, stateManagerHelper, messageRouter, mediatorInputService)
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
        val input = mapOf("key" to EventProcessingInput("key", getStringRecords(1, "key"), inputState1))
        eventProcessor.processEvents(input)

        verify(stateManagerHelper, times(1)).deserializeValue(any())
        verify(stateAndEventProcessor, times(4)).onNext(anyOrNull(), any())
        verify(messageRouter, times(9)).getDestination(any())
        verify(client, times(3)).send(any())
        verify(mediatorInputService, times(1)).getHash<String, String>(any())
        verify(stateManagerHelper, times(1)).createOrUpdateState(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `sync processing fails with partially created state, the output contains this state with processing failure`() {
        val mergedState = mock<State>()
        val mockState = mock<State>()
        val input = mapOf("key" to EventProcessingInput("key", getStringRecords(1, "key"), null))

        val updatedState = mock<StateAndEventProcessor.State<String>>()
        whenever(client.send(any())).thenThrow(CordaMessageAPIIntermittentException("baz"))
        whenever(stateAndEventProcessor.onNext(anyOrNull(), any())).thenAnswer {
            Response(
                updatedState,
                listOf(
                    Record("", "key", syncMessage)
                )
            )
        }
        whenever(stateManagerHelper.createOrUpdateState(any(), eq(null), eq(updatedState))).thenReturn(mergedState)
        whenever(stateManagerHelper.failStateProcessing(any(), eq(mergedState), any())).thenReturn(mockState)

        val outputMap = eventProcessor.processEvents(input)

        val output = outputMap["key"]
        assertEquals(emptyList<MediatorMessage<Any>>(), output?.asyncOutputs)
        assertThat(output?.stateChangeAndOperation?.outputState).isEqualTo(mockState)
        assertThat(output?.stateChangeAndOperation).isInstanceOf(StateChangeAndOperation.Create::class.java)
    }

    @Test
    fun `sync processing fails with no state, an empty state is output with processing failure`() {
        val mockedState = mock<State>()
        val input = mapOf("key" to EventProcessingInput("key", getStringRecords(1, "key"), null))

        whenever(client.send(any())).thenThrow(CordaMessageAPIIntermittentException("baz"))
        whenever(stateAndEventProcessor.onNext(anyOrNull(), any())).thenAnswer {
            Response<State>(
                null,
                listOf(
                    Record("", "key", syncMessage)
                )
            )
        }
        whenever(stateManagerHelper.failStateProcessing(any(), eq(null), any())).thenReturn(mockedState)

        val outputMap = eventProcessor.processEvents(input)

        val output = outputMap["key"]
        assertEquals(emptyList<MediatorMessage<Any>>(), output?.asyncOutputs)
        assertThat(output?.stateChangeAndOperation?.outputState).isEqualTo(mockedState)
        assertThat(output?.stateChangeAndOperation).isInstanceOf(StateChangeAndOperation.Create::class.java)
    }

    @Test
    fun `sync processing fails on second loop and uses current processor state with processing failure`() {
        val input = mapOf("key" to EventProcessingInput("key", getStringRecords(2, "key"), null))
        val firstLoopUpdatedState = mock<StateAndEventProcessor.State<String>>()
        val mergedState = mock<State>()
        val mockedState = mock<State>()

        whenever(stateAndEventProcessor.onNext(anyOrNull(), any()))
            .thenAnswer { Response(firstLoopUpdatedState, emptyList()) }
            .thenAnswer { Response<String>(null, listOf(Record("", "key", syncMessage))) }
        whenever(client.send(any())).thenThrow(CordaMessageAPIIntermittentException("baz"))
        whenever(stateManagerHelper.createOrUpdateState(any(), eq(null), eq(firstLoopUpdatedState))).thenReturn(mergedState)
        whenever(stateManagerHelper.failStateProcessing(any(), eq(mergedState), any())).thenReturn(mockedState)

        val outputMap = eventProcessor.processEvents(input)

        val output = outputMap["key"]
        assertEquals(emptyList<MediatorMessage<Any>>(), output?.asyncOutputs)
        assertThat(output?.stateChangeAndOperation?.outputState).isEqualTo(mockedState)
        assertThat(output?.stateChangeAndOperation).isInstanceOf(StateChangeAndOperation.Create::class.java)
    }

    private fun buildTestConfig() = EventMediatorConfig(
        "",
        SmartConfigImpl.empty(),
        emptyList(),
        emptyList(),
        stateAndEventProcessor,
        mock<MessageRouterFactory>(),
        1,
        "",
        mock(),
        20
    )
}
