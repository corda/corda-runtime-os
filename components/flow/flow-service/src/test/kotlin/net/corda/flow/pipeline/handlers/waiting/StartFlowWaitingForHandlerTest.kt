package net.corda.flow.pipeline.handlers.waiting

import net.corda.data.flow.FlowKey
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StartFlowWaitingForHandlerTest {
    @Test
    fun `Returns a FlowContinuation#Run`() {
        val flowKey = FlowKey("flow id", HoldingIdentity("x500 name", "group id"))
        val continuation = StartFlowWaitingForHandler().runOrContinue(
            buildFlowEventContext(
                checkpoint = Checkpoint().apply {
                    this.flowKey = flowKey
                },
                inputEventPayload = Unit
            ),
            WaitingForStartFlow
        )

        assertEquals(FlowContinuation.Run(Unit), continuation)
    }
}