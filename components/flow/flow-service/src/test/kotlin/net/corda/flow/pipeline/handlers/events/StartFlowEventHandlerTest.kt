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
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class StartFlowEventHandlerTest {
    private val holdingIdentity = BOB_X500_HOLDING_IDENTITY
    private val startFlow = StartFlow(
        FlowStartContext().apply {
             identity = holdingIdentity
        },
        "start args")
    private val flowId = "flow id"

    @Test
    fun `initialises the flow checkpoint from the avro checkpoint`() {
        val waitingForExpected = WaitingFor(WaitingForStartFlow)
        val contextExpected = buildFlowEventContext(mock(), inputEventPayload = startFlow, flowId = flowId)
        val fakeCheckpointInitializer = FakeCheckpointInitializerService(
            startFlow.startContext,
            waitingForExpected,
            holdingIdentity.toCorda(),
            contextExpected.checkpoint
        )
        val handler = StartFlowEventHandler(fakeCheckpointInitializer)
        val actualContext = handler.preProcess(contextExpected)
        assertThat(actualContext).isEqualTo(contextExpected)
    }

    private class FakeCheckpointInitializerService(
        val startContextExpected: FlowStartContext,
        val waitingForExpected: WaitingFor,
        val holdingIdentityExpected: HoldingIdentity,
        val checkpointExpected: FlowCheckpoint
    )
        : CheckpointInitializer {

        override fun initialize(
            checkpoint: FlowCheckpoint,
            waitingFor: WaitingFor,
            holdingIdentity: HoldingIdentity,
            contextBuilder: (Set<SecureHash>) -> FlowStartContext
        ) {
            val startContext = contextBuilder(emptySet())
            assertThat(checkpoint).isEqualTo(checkpointExpected)
            assertThat(waitingFor).isEqualTo(waitingForExpected)
            assertThat(holdingIdentity).isEqualTo(holdingIdentityExpected)
            assertThat(startContext).isEqualTo(startContextExpected)

        }
    }
}