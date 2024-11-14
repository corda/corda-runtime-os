package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.external.ExternalEventRetryRequest
import net.corda.data.flow.state.external.ExternalEventState
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class ExternalEventRetryRequestHandlerTest {

    private val externalEventRetryRequest = ExternalEventRetryRequest("requestId", Instant.now())

    private val checkpoint = mock<FlowCheckpoint>()
    private val externalEventRetryRequestHandler = ExternalEventRetryRequestHandler()

    @Test
    fun `does not throw a flow event exception if the checkpoint exists and it is the correct request id`() {
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(checkpoint.externalEventState).thenReturn(ExternalEventState().apply { requestId = "requestId" })
        val context = buildFlowEventContext(checkpoint, externalEventRetryRequest)

        externalEventRetryRequestHandler.preProcess(context)
    }

    @Test
    fun `does not throw a flow event exception if the checkpoint exists and it a token request id`() {
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(checkpoint.externalEventState).thenReturn(ExternalEventState().apply { requestId = "requestId" })

        val context = buildFlowEventContext(checkpoint, ExternalEventRetryRequest("TokenRetry", Instant.now()))
        externalEventRetryRequestHandler.preProcess(context)
    }

    @Test
    fun `throws a flow event exception if the checkpoint does not exist`() {
        whenever(checkpoint.doesExist).thenReturn(false)

        val context = buildFlowEventContext(checkpoint, externalEventRetryRequest)

        assertThrows<FlowEventException> {
            externalEventRetryRequestHandler.preProcess(context)
        }
    }

    @Test
    fun `throws a flow event exception if the flow is not waiting for an external event response`() {
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(checkpoint.externalEventState).thenReturn(null)

        val context = buildFlowEventContext(checkpoint, externalEventRetryRequest)

        assertThrows<FlowEventException> {
            externalEventRetryRequestHandler.preProcess(context)
        }
    }

    @Test
    fun `throws a flow event exception if the flow is waiting for a different external event response`() {
        whenever(checkpoint.doesExist).thenReturn(true)
        whenever(checkpoint.externalEventState).thenReturn(ExternalEventState().apply { requestId = "OtherRequestId" })

        val context = buildFlowEventContext(checkpoint, externalEventRetryRequest)

        assertThrows<FlowEventException> {
            externalEventRetryRequestHandler.preProcess(context)
        }
    }

}