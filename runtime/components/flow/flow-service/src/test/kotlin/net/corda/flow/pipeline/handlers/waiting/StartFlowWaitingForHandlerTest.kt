package net.corda.flow.pipeline.handlers.waiting

import net.corda.data.flow.event.Wakeup
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class StartFlowWaitingForHandlerTest {

    @Test
    fun `Returns a FlowContinuation#Run`() {
        val inputContext = buildFlowEventContext(
            checkpoint = mock(),
            inputEventPayload = Wakeup()
        )
        val continuation = StartFlowWaitingForHandler().runOrContinue(inputContext, WaitingForStartFlow)
        assertEquals(FlowContinuation.Run(Unit), continuation)
    }
}