package net.corda.flow.pipeline.handlers.waiting

import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.FlowWaitingForHandlerTestContext
import net.corda.flow.fiber.FlowContinuation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WakeupWaitingForHandlerTest {

    @Test
    fun `Returns a FlowContinuation#Run`() {
        val testContext = FlowWaitingForHandlerTestContext(Wakeup())
        val continuation = WakeupWaitingForHandler().runOrContinue(testContext.flowEventContext, testContext.waitingFor)
        assertEquals(FlowContinuation.Run(Unit), continuation)
    }
}