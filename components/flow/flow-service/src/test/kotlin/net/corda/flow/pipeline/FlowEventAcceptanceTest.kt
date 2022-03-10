package net.corda.flow.pipeline

import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.event.Wakeup
import net.corda.flow.acceptance.dsl.filterOutputFlowTopicEventPayloads
import net.corda.flow.acceptance.dsl.flowEventDSL
import net.corda.flow.fiber.FlowIORequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowEventAcceptanceTest {

    @Test
    fun `forcing a checkpoint schedules a wakeup event`() {
        flowEventDSL {

            startedFlowFiber {
                repeatSuspension(FlowIORequest.ForceCheckpoint, 1)
            }

            inputLastOutputEvent()

            val response = processOne()
            val outputPayloads = response.filterOutputFlowTopicEventPayloads()

            assertEquals(net.corda.data.flow.state.waiting.Wakeup(), response.updatedState?.flowState?.waitingFor?.value)
            assertEquals(1, outputPayloads.size)
            assertTrue(outputPayloads.single() is Wakeup)
        }
    }

    @Test
    fun `finishing a subFlow suspends the flow and schedules a wakeup event`() {
        flowEventDSL {

            startedFlowFiber {
                queueSuspension(FlowIORequest.SubFlowFinished(FlowStackItem()))
            }

            inputLastOutputEvent()

            val response = processOne()
            val outputPayloads = response.filterOutputFlowTopicEventPayloads()

            assertEquals(net.corda.data.flow.state.waiting.Wakeup(), response.updatedState?.flowState?.waitingFor?.value)
            assertEquals(1, outputPayloads.size)
            assertTrue(outputPayloads.single() is Wakeup)
        }
    }
}