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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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

    private val state1: State = mock()
    private val asyncMessage: String = "ASYNC_PAYLOAD"
    private val syncMessage: String = "SYNC_PAYLOAD"

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
                StateAndEventProcessor.State("bar", null), listOf(
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
        val input = mapOf("key" to EventProcessingInput("key", getStringRecords(1, "key"), state1))
        eventProcessor.processEvents(input)

        verify(stateManagerHelper, times(1)).deserializeValue(any())
        verify(stateAndEventProcessor, times(4)).onNext(anyOrNull(), any())
        verify(messageRouter, times(9)).getDestination(any())
        verify(client, times(3)).send(any())
        verify(mediatorInputService, times(1)).getHash<String, String>(any())
        verify(stateManagerHelper, times(1)).createOrUpdateState(any(), anyOrNull(), anyOrNull())
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
