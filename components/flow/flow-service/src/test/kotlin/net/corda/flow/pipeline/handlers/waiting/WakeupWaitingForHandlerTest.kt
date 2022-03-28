package net.corda.flow.pipeline.handlers.waiting

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WakeupWaitingForHandlerTest {

    @Test
    fun `Returns a FlowContinuation#Run`() {
        val flowKey = FlowKey("flow id", HoldingIdentity("x500 name", "group id"))
        val continuation = WakeupWaitingForHandler().runOrContinue(
            FlowEventContext(
                checkpoint = Checkpoint().apply {
                    this.flowKey = flowKey
                },
                inputEvent = FlowEvent(flowKey, Unit),
                inputEventPayload = Unit,
                outputRecords = emptyList()
            ),
            Wakeup()
        )

        assertEquals(FlowContinuation.Run(Unit), continuation)
    }
}