package net.corda.flow.pipeline.handlers.waiting

import net.corda.flow.FlowWaitingForHandlerTestContext
import net.corda.flow.fiber.FlowContinuation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StartFlowWaitingForHandlerTest {
    @Test
    fun `Returns a FlowContinuation#Run`() {
        val testContext = FlowWaitingForHandlerTestContext(WaitingForStartFlow)
        val continuation = StartFlowWaitingForHandler().runOrContinue(testContext.flowEventContext, testContext.waitingFor)
        assertEquals(FlowContinuation.Run(Unit), continuation)

        assertEquals(FlowContinuation.Run(Unit), continuation)
    }
}