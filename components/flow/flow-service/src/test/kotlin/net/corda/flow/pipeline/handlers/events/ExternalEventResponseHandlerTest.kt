package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.flow.REQUEST_ID_1
import net.corda.flow.external.events.impl.ExternalEventManager
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExternalEventResponseHandlerTest {

    private val externalEventResponse = ExternalEventResponse()

    private val checkpoint = mock<FlowCheckpoint>()
    private val externalEventManager = mock<ExternalEventManager>()
    private val externalEventResponseHandler = ExternalEventResponseHandler(externalEventManager)

    @Test
    fun `throws a flow event exception if the checkpoint does not exist`() {
        whenever(checkpoint.doesExist).thenReturn(false)

        val context = buildFlowEventContext(checkpoint, externalEventResponse)

        assertThrows<FlowEventException> {
            externalEventResponseHandler.preProcess(context)
        }
    }

    @Test
    fun `throws a flow event exception if the flow is not waiting for an external event response`() {
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(checkpoint.externalEventState).thenReturn(null)

        val context = buildFlowEventContext(checkpoint, externalEventResponse)

        assertThrows<FlowEventException> {
            externalEventResponseHandler.preProcess(context)
        }
    }

    @Test
    fun `processes the received event if the flow is waiting for an external event response`() {
        val externalEventState = ExternalEventState()
        val updatedExternalEventState = ExternalEventState().apply { REQUEST_ID_1 }
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(checkpoint.externalEventState).thenReturn(externalEventState)
        whenever(externalEventManager.processEventReceived(externalEventState, externalEventResponse)).thenReturn(updatedExternalEventState)

        val context = buildFlowEventContext(checkpoint, externalEventResponse)

        externalEventResponseHandler.preProcess(context)
        verify(checkpoint).externalEventState = updatedExternalEventState
    }
}