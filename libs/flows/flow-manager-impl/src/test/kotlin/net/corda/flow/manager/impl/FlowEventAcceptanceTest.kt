package net.corda.flow.manager.impl

import net.corda.data.flow.FlowStackItem
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.flow.manager.impl.acceptance.dsl.filterOutputFlowTopicEventPayloads
import net.corda.flow.manager.impl.acceptance.dsl.flowEventDSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowEventAcceptanceTest {

    @Test
    fun `receiving multiple wakeup events does not alter a flow's checkpoint`() {
        flowEventDSL {

            startedFlowFiber {
                repeatSuspension(FlowIORequest.ForceCheckpoint, 3)
            }

            inputLastOutputEvent()
            inputLastOutputEvent()
            inputLastOutputEvent()

            val responses = processAll()

            val checkpoints = responses.map { it.updatedState }
            val outputEvents = responses.map { it.responseEvents.single().value as FlowEvent }

            assertEquals(3, checkpoints.size)
            assertEquals(1, checkpoints.toSet().size)
            assertEquals(3, outputEvents.size)
            assertEquals(3, outputEvents.map { it.payload }.filterIsInstance<Wakeup>().size)
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