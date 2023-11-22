package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.external.ExternalEventRetry
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.data.flow.state.external.ExternalEventStateStatus
import net.corda.data.flow.state.external.ExternalEventStateType
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ExternalEventRetryHandlerTest {

    private val checkpoint = mock<FlowCheckpoint>()
    private val handler = ExternalEventRetryHandler()
    private val externalEventRetry = ExternalEventRetry()

    // todo cs add for other logic
    @Test
    fun `external event retry handler receives event with null external event state`() {
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(checkpoint.externalEventState).thenReturn(null)

        val context = buildFlowEventContext(checkpoint, externalEventRetry)

        assertThrows<FlowEventException> {
            handler.preProcess(context)
        }
    }

    @Test
    fun `external event retry handler receives event with OK external event state`() {
        val externalEventState = ExternalEventState().apply {
            this.status = ExternalEventStateStatus(ExternalEventStateType.OK, null)
        }
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(checkpoint.externalEventState).thenReturn(externalEventState)

        val context = buildFlowEventContext(checkpoint, externalEventRetry)

        assertThrows<FlowEventException> {
            handler.preProcess(context)
        }
    }

    @Test
    fun `external event retry handler receives validates and prepares checkpoint for RETRYING`() {
        val externalEventState = ExternalEventState().apply {
            this.status = ExternalEventStateStatus(ExternalEventStateType.RETRY, null)
        }
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(checkpoint.externalEventState).thenReturn(externalEventState)

        val context = buildFlowEventContext(checkpoint, externalEventRetry)

        handler.preProcess(context)
        // todo cs add assertions
    }

}