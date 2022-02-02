package net.corda.flow.manager.impl.acceptance

import net.corda.data.flow.state.Checkpoint
import net.corda.flow.manager.FlowEventProcessor
import net.corda.flow.manager.impl.acceptance.dsl.MockFlowEventProcessor
import net.corda.flow.manager.impl.acceptance.dsl.MockFlowRunner
import net.corda.messaging.api.processor.StateAndEventProcessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MockFlowEventProcessorTest {

    private companion object {
        const val FLOW_ID = "flow id"
        const val CLIENT_ID = "client id"
    }

    private val runner = MockFlowRunner()
    private val delegate: FlowEventProcessor = mock()
    private val processor = MockFlowEventProcessor(delegate, runner)

    @Test
    fun `startFlow creates and executes the first step of a new flow`() {
        whenever(delegate.onNext(eq(null), any())).thenReturn(StateAndEventProcessor.Response(Checkpoint(), emptyList()))
        val (fiber, _) = processor.startFlow(FLOW_ID, CLIENT_ID)
        assertEquals(FLOW_ID, fiber.flowId)
    }
}