package net.corda.flow.pipeline

import net.corda.flow.test.utils.buildFlowEventContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class FlowEventContextTest {

    @Test
    fun `flow not to be killed if flow terminated context is null`(){
        val context= buildFlowEventContext(
            checkpoint= mock(),
            inputEventPayload = Any(),
            outputRecords = emptyList(),
            sendToDlq = true
        )

        assertFalse(context.isFlowToBeKilled())
    }

    @Test
    fun `flow to be killed if flow terminated context is TO_BE_KILLED`(){
        val context= buildFlowEventContext(
            checkpoint= mock(),
            inputEventPayload = Any(),
            outputRecords = emptyList(),
            sendToDlq = true,
            flowTerminatedContext = FlowTerminatedContext(FlowTerminatedContext.TerminationStatus.TO_BE_KILLED)
        )

        assertTrue(context.isFlowToBeKilled())
    }
}