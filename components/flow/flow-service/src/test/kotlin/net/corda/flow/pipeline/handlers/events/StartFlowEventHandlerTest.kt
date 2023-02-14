package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.BOB_X500_HOLDING_IDENTITY
import net.corda.flow.pipeline.CheckpointInitializer
import net.corda.flow.pipeline.handlers.waiting.WaitingForStartFlow
import net.corda.flow.state.FlowCheckpoint
import net.corda.flow.test.utils.buildFlowEventContext
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FakeCheckpointInitializerService
 : CheckpointInitializer {
    override fun initialize(
        checkpoint: FlowCheckpoint,
        waitingFor: WaitingFor,
        holdingIdentity: HoldingIdentity,
        contextBuilder: (Set<SecureHash>) -> FlowStartContext
    ) {
        val startContext = FlowStartContext().apply {
            identity = holdingIdentity.toAvro()

        }
        checkpoint.waitingFor = waitingFor
    }


}
class StartFlowEventHandlerTest {
    private val holdingIdentity = BOB_X500_HOLDING_IDENTITY
    private val startFlow = StartFlow(
        FlowStartContext().apply {
             identity = holdingIdentity
        },
        "start args")

    private val flowId = "flow id"
    private val checkpointInitializer = mock<CheckpointInitializer>()
    private val handler = StartFlowEventHandler(checkpointInitializer)


    @Test
    fun `initialises the flow checkpoint from the avro checkpoint`() {
        val context = buildFlowEventContext(mock(), inputEventPayload = startFlow, flowId = flowId)
        handler.preProcess(context)
        verify(checkpointInitializer).initialize(
            context.checkpoint,
            WaitingFor(WaitingForStartFlow),
            context.inputEventPayload.startContext.identity.toCorda(),
            {
                context.inputEventPayload.startContext
            }
        )
    }

    @Test
    fun `when in a retry still set the flow context`() {
        val context = buildFlowEventContext(mock(), inputEventPayload = startFlow, flowId = flowId)
        whenever(context.checkpoint.inRetryState).thenReturn(true)
        handler.preProcess(context)
        verify(checkpointInitializer).initialize(
            context.checkpoint,
            WaitingFor(WaitingForStartFlow),
            context.inputEventPayload.startContext.identity.toCorda(),
            {
                context.inputEventPayload.startContext
            }
        )

    }
}