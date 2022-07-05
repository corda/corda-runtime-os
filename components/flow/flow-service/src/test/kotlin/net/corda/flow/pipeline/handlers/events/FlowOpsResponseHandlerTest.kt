package net.corda.flow.pipeline.handlers.events

import net.corda.crypto.manager.CryptoManager
import net.corda.data.crypto.wire.CryptoResponseContext
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.flow.state.crypto.CryptoState
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class FlowOpsResponseHandlerTest {

    private val checkpoint = mock<FlowCheckpoint>()
    private val cryptoManager = mock<CryptoManager>()
    private val flowOpsResponseHandler = FlowOpsResponseHandler(cryptoManager)

    @Test
    fun `CryptoState was null`() {
        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = FlowOpsResponse().apply {
                context = CryptoResponseContext().apply {
                    requestId = "request1"
                }
            }
        )

        whenever(checkpoint.cryptoState).thenReturn(null)

        flowOpsResponseHandler.preProcess(inputContext)

        verify(cryptoManager, never()).processMessageReceived(any(), any())
    }

    @Test
    fun `Process FlowOpsResponse`() {
        val instant = Instant.now()
        val response = FlowOpsResponse()
        val cryptoState = CryptoState("request1", instant, FlowOpsRequest(), 0, null)
        val updatedCryptoState = CryptoState("request1", instant, FlowOpsRequest(), 0, response)

        val inputContext = buildFlowEventContext(
            checkpoint = checkpoint,
            inputEventPayload = response
        )

        whenever(checkpoint.cryptoState).thenReturn(cryptoState)
        whenever(cryptoManager.processMessageReceived(cryptoState, response)).thenReturn(updatedCryptoState)

        flowOpsResponseHandler.preProcess(inputContext)

        verify(checkpoint).cryptoState = updatedCryptoState
    }
}